using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Media;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Tasks;

namespace Kotoba {
 /// <summary>Portable, offline neural Japanese speech. Each new request supersedes the previous one.</summary>
 public sealed class VoiceService : IDisposable {
  readonly object gate = new object();
  readonly string voiceDirectory, cacheDirectory, executable;
  Process activeProcess;
  SoundPlayer player;
  long generation;
  bool disposed;
  string lastError = "";
  const string ModelIdentity = "supertonic3-int8-20260511-ja-sid0-speed095-steps8-utf8-v2";
  static readonly string[] RequiredModelFiles = { "duration_predictor.int8.onnx", "text_encoder.int8.onnx", "vector_estimator.int8.onnx", "vocoder.int8.onnx", "tts.json", "unicode_indexer.bin", "voice.bin" };

  public VoiceService(string baseDirectory) : this(baseDirectory, Path.Combine(baseDirectory, "portable-data", "voice-cache")) { }
  public VoiceService(string baseDirectory, string cacheDirectory) {
   if(String.IsNullOrWhiteSpace(baseDirectory)) throw new ArgumentException("Base directory is required.", "baseDirectory");
   if(String.IsNullOrWhiteSpace(cacheDirectory)) throw new ArgumentException("Cache directory is required.", "cacheDirectory");
   voiceDirectory=Path.Combine(Path.GetFullPath(baseDirectory),"voice");
   this.cacheDirectory=Path.GetFullPath(cacheDirectory);
   executable=Path.Combine(voiceDirectory,"VoiceHost.exe");
  }
  public string LastError { get { lock(gate) return lastError; } }
  public bool IsAvailable { get {
   lock(gate) { if(disposed) return false; }
   return Environment.Is64BitOperatingSystem && File.Exists(executable)
    && File.Exists(Path.Combine(voiceDirectory,"onnxruntime.dll"))
    && File.Exists(Path.Combine(voiceDirectory,"sherpa-onnx-c-api.dll"))
    && RequiredModelFiles.All(n=>File.Exists(Path.Combine(voiceDirectory,"model",n)));
  } }

  // Also useful for pre-generating a word without playing it. It shares the same cancellation semantics.
  public Task<string> PrepareAsync(string text, string cacheKey) {
   long request=BeginRequest();
   return Task.Run(()=>Generate(text,cacheKey,request));
  }
  public async Task<bool> PlayAsync(string text, string cacheKey) {
   long request=BeginRequest();
   string path=await Task.Run(()=>Generate(text,cacheKey,request)).ConfigureAwait(false);
   if(path==null) return false;
   SoundPlayer next=null;
   try {
    next=new SoundPlayer(path);
    next.Load();
    lock(gate) {
     if(!IsCurrentLocked(request)) { next.Dispose(); return false; }
     player=next;
     player.Play();
     return true;
    }
   } catch(Exception ex) {
    lock(gate) { if(player==next) player=null; }
    if(next!=null) next.Dispose();
    SetError(request,"音频无法播放："+ex.Message);
    return false;
   }
  }
  long BeginRequest() {
   lock(gate) {
    generation++;
    StopLocked();
    lastError="";
    return generation;
   }
  }
  bool IsCurrentLocked(long request) { return !disposed && generation==request; }
  bool IsCurrent(long request) { lock(gate) return IsCurrentLocked(request); }
  void SetError(long request,string message) { lock(gate) if(IsCurrentLocked(request)) lastError=message; }
  public void Stop() { lock(gate) { generation++; StopLocked(); } }
  void StopLocked() {
   if(player!=null) { try { player.Stop(); } catch { } player.Dispose(); player=null; }
   if(activeProcess!=null) { try { if(!activeProcess.HasExited) activeProcess.Kill(); } catch(InvalidOperationException) { } catch(System.ComponentModel.Win32Exception) { } }
  }
  public void Dispose() { lock(gate) { if(disposed) return; disposed=true; generation++; StopLocked(); } }

  string Generate(string text,string cacheKey,long request) {
   Process process=null;
   string temporary=null;
   try {
    if(!IsCurrent(request)) return null;
    if(String.IsNullOrWhiteSpace(text)) { SetError(request,"这个词没有可朗读的假名。"); return null; }
    text=text.Trim();
    if(text.Length>400 || text.IndexOf('\0')>=0) { SetError(request,"朗读文本过长或包含无效字符。"); return null; }
    if(!IsAvailable) { SetError(request,"离线神经语音文件不完整。请完整解压包含 voice 文件夹的便携包。"); return null; }
    Directory.CreateDirectory(cacheDirectory);
    string cachePath=Path.Combine(cacheDirectory,Hash(ModelIdentity+"\n"+(cacheKey??"")+"\n"+text)+".wav");
    if(IsWave(cachePath)) return IsCurrent(request)?cachePath:null;
    string temporaryName="pending-"+Guid.NewGuid().ToString("N")+".wav";
    temporary=Path.Combine(cacheDirectory,temporaryName);
    var args=new StringBuilder();
    Add(args,Convert.ToBase64String(Encoding.UTF8.GetBytes(text)));
    Add(args,temporary);
    process=new Process();
    process.StartInfo=new ProcessStartInfo(executable,args.ToString()) {
     WorkingDirectory=voiceDirectory, UseShellExecute=false, CreateNoWindow=true,
     WindowStyle=ProcessWindowStyle.Hidden, RedirectStandardOutput=true, RedirectStandardError=true
    };
    lock(gate) {
     if(!IsCurrentLocked(request)) return null;
     process.Start();
     activeProcess=process;
    }
    Task<string> stdout=process.StandardOutput.ReadToEndAsync();
    Task<string> stderr=process.StandardError.ReadToEndAsync();
    var timer=Stopwatch.StartNew();
    while(!process.WaitForExit(60)) {
     if(!IsCurrent(request)) { Kill(process); return null; }
     if(timer.Elapsed.TotalSeconds>45) { Kill(process); SetError(request,"语音合成超时。可按 R 重试，或在设置中选择 Windows 备用语音。"); return null; }
    }
    if(!IsCurrent(request)) return null;
    // Drain redirected streams before releasing the process; generated text never goes to a network service.
    stdout.GetAwaiter().GetResult();
    string diagnostic=stderr.GetAwaiter().GetResult();
    if(process.ExitCode!=0 || !IsWave(temporary)) {
     SetError(request,"离线语音合成失败（代码 "+process.ExitCode+"）。可按 R 重试，或选择 Windows 备用语音。");
     return null;
    }
    if(!IsCurrent(request)) return null;
    // Serialize only cache publication across app/prefetch instances; synthesis stays parallel.
    using(var mutex=new System.Threading.Mutex(false,"Local\\KotobaVoiceCache-"+Hash(cachePath.ToUpperInvariant()))) {
     bool acquired=false;
     try {
      try { acquired=mutex.WaitOne(3000); } catch(System.Threading.AbandonedMutexException) { acquired=true; }
      if(!acquired) throw new IOException("Audio cache is busy. Please retry.");
      if(!IsCurrent(request)) return null;
      if(!IsWave(cachePath)) {
       if(File.Exists(cachePath)) File.Delete(cachePath);
       File.Move(temporary,cachePath); temporary=null;
      }
     } finally { if(acquired) mutex.ReleaseMutex(); }
    }
    TrimCache(cachePath);
    return IsCurrent(request)?cachePath:null;
   } catch(Exception ex) {
    SetError(request,"语音暂不可用："+ex.Message);
    return null;
   } finally {
    if(process!=null) {
     lock(gate) { if(activeProcess==process) activeProcess=null; }
     Kill(process);
     process.Dispose();
    }
    if(temporary!=null) { try { if(File.Exists(temporary)) File.Delete(temporary); } catch { } }
   }
  }
  static void Kill(Process p) { try { if(!p.HasExited) { p.Kill(); p.WaitForExit(1000); } } catch(InvalidOperationException) { } catch(System.ComponentModel.Win32Exception) { } }
  static bool IsWave(string path) {
   try {
    using(var f=File.OpenRead(path)) using(var r=new BinaryReader(f)) {
     if(f.Length<48 || f.Length>20*1024*1024) return false;
     return r.ReadUInt32()==0x46464952 && r.ReadUInt32()+8==f.Length && r.ReadUInt32()==0x45564157;
    }
   } catch { return false; }
  }
  static string Hash(string text) { using(var sha=SHA256.Create()) return BitConverter.ToString(sha.ComputeHash(Encoding.UTF8.GetBytes(text))).Replace("-","").ToLowerInvariant(); }
  static void Add(StringBuilder b,string value) {
   // Windows command-line quoting; no shell is involved.
   b.Append('"'); int slashes=0;
   foreach(char c in value) {
    if(c=='\\') { slashes++; continue; }
    if(c=='"') { b.Append('\\',slashes*2+1); b.Append('"'); }
    else { b.Append('\\',slashes); b.Append(c); }
    slashes=0;
   }
   b.Append('\\',slashes*2); b.Append('"'); b.Append(' ');
  }
  void TrimCache(string keep) {
   try {
    var files=new DirectoryInfo(cacheDirectory).GetFiles("*.wav").OrderBy(f=>f.LastWriteTimeUtc).ToList();
    long bytes=files.Sum(f=>f.Length);
    foreach(var f in files) {
     if(bytes<=128L*1024*1024) break;
     if(String.Equals(f.FullName,keep,StringComparison.OrdinalIgnoreCase)) continue;
     long size=f.Length; f.Delete(); bytes-=size;
    }
   } catch { /* Cache housekeeping must never prevent speech. */ }
  }
 }
}
