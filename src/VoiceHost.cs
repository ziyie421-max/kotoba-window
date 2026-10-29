// Native ABI definitions are derived from sherpa-onnx v2.0.1 c-api.h (Apache-2.0).
// Text is transported as Base64 UTF-8; WAV files are written with Unicode .NET paths.
using System;
using System.IO;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
[StructLayout(LayoutKind.Sequential)]
struct SherpaOnnxOfflineTtsVitsModelConfig {
 public IntPtr model;
 public IntPtr lexicon;
 public IntPtr tokens;
 public IntPtr data_dir;
 public float noise_scale;
 public float noise_scale_w;
 public float length_scale;
 public IntPtr dict_dir;
}
[StructLayout(LayoutKind.Sequential)]
struct SherpaOnnxOfflineTtsMatchaModelConfig {
 public IntPtr acoustic_model;
 public IntPtr vocoder;
 public IntPtr lexicon;
 public IntPtr tokens;
 public IntPtr data_dir;
 public float noise_scale;
 public float length_scale;
 public IntPtr dict_dir;
}
[StructLayout(LayoutKind.Sequential)]
struct SherpaOnnxOfflineTtsKokoroModelConfig {
 public IntPtr model;
 public IntPtr voices;
 public IntPtr tokens;
 public IntPtr data_dir;
 public float length_scale;
 public IntPtr dict_dir;
 public IntPtr lexicon;
 public IntPtr lang;
}
[StructLayout(LayoutKind.Sequential)]
struct SherpaOnnxOfflineTtsKittenModelConfig {
 public IntPtr model;
 public IntPtr voices;
 public IntPtr tokens;
 public IntPtr data_dir;
 public float length_scale;
 public IntPtr lexicon;
}
[StructLayout(LayoutKind.Sequential)]
struct SherpaOnnxOfflineTtsZipvoiceModelConfig {
 public IntPtr tokens;
 public IntPtr encoder;
 public IntPtr decoder;
 public IntPtr vocoder;
 public IntPtr data_dir;
 public IntPtr lexicon;
 public float feat_scale;
 public float t_shift;
 public float target_rms;
 public float guidance_scale;
}
[StructLayout(LayoutKind.Sequential)]
struct SherpaOnnxOfflineTtsPocketModelConfig {
 public IntPtr lm_flow;
 public IntPtr lm_main;
 public IntPtr encoder;
 public IntPtr decoder;
 public IntPtr text_conditioner;
 public IntPtr vocab_json;
 public IntPtr token_scores_json;
 public int voice_embedding_cache_capacity;
}
[StructLayout(LayoutKind.Sequential)]
struct SherpaOnnxOfflineTtsSupertonicModelConfig {
 public IntPtr duration_predictor;
 public IntPtr text_encoder;
 public IntPtr vector_estimator;
 public IntPtr vocoder;
 public IntPtr tts_json;
 public IntPtr unicode_indexer;
 public IntPtr voice_style;
}
[StructLayout(LayoutKind.Sequential)]
struct SherpaOnnxOfflineTtsModelConfig {
 public SherpaOnnxOfflineTtsVitsModelConfig vits;
 public int num_threads;
 public int debug;
 public IntPtr provider;
 public SherpaOnnxOfflineTtsMatchaModelConfig matcha;
 public SherpaOnnxOfflineTtsKokoroModelConfig kokoro;
 public SherpaOnnxOfflineTtsKittenModelConfig kitten;
 public SherpaOnnxOfflineTtsZipvoiceModelConfig zipvoice;
 public SherpaOnnxOfflineTtsPocketModelConfig pocket;
 public SherpaOnnxOfflineTtsSupertonicModelConfig supertonic;
}
[StructLayout(LayoutKind.Sequential)]
struct SherpaOnnxOfflineTtsConfig {
 public SherpaOnnxOfflineTtsModelConfig model;
 public IntPtr rule_fsts;
 public int max_num_sentences;
 public IntPtr rule_fars;
 public float silence_scale;
}
[StructLayout(LayoutKind.Sequential)]
struct SherpaOnnxGeneratedAudio {
 public IntPtr samples;
 public int n;
 public int sample_rate;
}
[StructLayout(LayoutKind.Sequential)]
struct SherpaOnnxGenerationConfig {
 public float silence_scale;
 public float speed;
 public int sid;
 public IntPtr reference_audio;
 public int reference_audio_len;
 public int reference_sample_rate;
 public IntPtr reference_text;
 public int num_steps;
 public IntPtr extra;
 public IntPtr phoneme_codepoints;
 public IntPtr phoneme_codepoints_lens;
 public int phoneme_codepoints_num_sentences;
 public IntPtr tokens;
 public IntPtr tokens_lens;
 public int tokens_num_sentences;
}
class VoiceHost {
 const string Dll="sherpa-onnx-c-api.dll";
 [DllImport(Dll,CallingConvention=CallingConvention.Cdecl)] static extern IntPtr SherpaOnnxCreateOfflineTts(ref SherpaOnnxOfflineTtsConfig config);
 [DllImport(Dll,CallingConvention=CallingConvention.Cdecl)] static extern IntPtr SherpaOnnxOfflineTtsGenerateWithConfig(IntPtr tts,IntPtr text,ref SherpaOnnxGenerationConfig config,IntPtr callback,IntPtr arg);
 [DllImport(Dll,CallingConvention=CallingConvention.Cdecl)] static extern void SherpaOnnxDestroyOfflineTtsGeneratedAudio(IntPtr audio);
 [DllImport(Dll,CallingConvention=CallingConvention.Cdecl)] static extern void SherpaOnnxDestroyOfflineTts(IntPtr tts);
 static List<IntPtr> owned=new List<IntPtr>();
 static IntPtr Utf8(string s){byte[] b=Encoding.UTF8.GetBytes(s+"\0");IntPtr p=Marshal.AllocHGlobal(b.Length);Marshal.Copy(b,0,p,b.Length);owned.Add(p);return p;}
 static void Main(string[] args){IntPtr tts=IntPtr.Zero,audio=IntPtr.Zero;try {
  if(args.Length!=2)throw new ArgumentException("Expected Base64 UTF-8 text and output WAV path.");
  string text=new UTF8Encoding(false,true).GetString(Convert.FromBase64String(args[0]));
  if(text.Length==0||text.Length>400)throw new ArgumentException("Invalid text length.");
  var c=new SherpaOnnxOfflineTtsConfig();
  c.model.num_threads=2;c.model.provider=Utf8("cpu");c.max_num_sentences=1;c.silence_scale=0.2f;
  c.model.supertonic.duration_predictor=Utf8("model/duration_predictor.int8.onnx");
  c.model.supertonic.text_encoder=Utf8("model/text_encoder.int8.onnx");
  c.model.supertonic.vector_estimator=Utf8("model/vector_estimator.int8.onnx");
  c.model.supertonic.vocoder=Utf8("model/vocoder.int8.onnx");
  c.model.supertonic.tts_json=Utf8("model/tts.json");
  c.model.supertonic.unicode_indexer=Utf8("model/unicode_indexer.bin");
  c.model.supertonic.voice_style=Utf8("model/voice.bin");
  var timer=System.Diagnostics.Stopwatch.StartNew();
  tts=SherpaOnnxCreateOfflineTts(ref c);if(tts==IntPtr.Zero)throw new Exception("Could not load neural voice.");
  long loadMs=timer.ElapsedMilliseconds;timer.Restart();
  var g=new SherpaOnnxGenerationConfig();g.sid=0;g.speed=0.95f;g.silence_scale=0.2f;g.num_steps=8;g.extra=Utf8("{\"lang\":\"ja\"}");
  audio=SherpaOnnxOfflineTtsGenerateWithConfig(tts,Utf8(text),ref g,IntPtr.Zero,IntPtr.Zero);
  long synthesisMs=timer.ElapsedMilliseconds;
  if(audio==IntPtr.Zero)throw new Exception("No generated audio.");
  var a=(SherpaOnnxGeneratedAudio)Marshal.PtrToStructure(audio,typeof(SherpaOnnxGeneratedAudio));
  if(a.n<1||a.n>44100*60||a.sample_rate<8000)throw new Exception("Invalid generated audio size.");
  var samples=new float[a.n];Marshal.Copy(a.samples,samples,0,a.n);
  using(var f=File.Create(args[1]))using(var w=new BinaryWriter(f)){
   w.Write(Encoding.ASCII.GetBytes("RIFF"));w.Write(36+a.n*2);w.Write(Encoding.ASCII.GetBytes("WAVEfmt "));w.Write(16);w.Write((short)1);w.Write((short)1);w.Write(a.sample_rate);w.Write(a.sample_rate*2);w.Write((short)2);w.Write((short)16);w.Write(Encoding.ASCII.GetBytes("data"));w.Write(a.n*2);
   foreach(float x in samples){if(Single.IsNaN(x)||Single.IsInfinity(x))throw new Exception("Non-finite audio sample.");w.Write((short)Math.Max(-32768,Math.Min(32767,Math.Round(x*32767.0))));}
  }
  Console.WriteLine("OK "+a.n+" "+a.sample_rate+" loadMs="+loadMs+" synthMs="+synthesisMs);
 }catch(Exception e){Console.Error.WriteLine(e.Message);Environment.ExitCode=1;}finally{if(audio!=IntPtr.Zero)SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio);if(tts!=IntPtr.Zero)SherpaOnnxDestroyOfflineTts(tts);foreach(IntPtr p in owned)Marshal.FreeHGlobal(p);}}
}
