using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Kotoba;
class VoiceServiceTests {
 static int passed,failed;
 static void Check(bool v,string s){if(!v)throw new Exception(s);}
 static void Test(string s,Action a){try{a();Console.WriteLine("PASS "+s);passed++;}catch(Exception e){Console.WriteLine("FAIL "+s+": "+e.Message);failed++;}}
 static bool Valid(string path){if(path==null||!File.Exists(path))return false;byte[] b=File.ReadAllBytes(path);if(b.Length<1000||b[0]!=82||b[1]!=73||b[2]!=70||b[3]!=70)return false;int peak=0;for(int i=44;i+1<b.Length;i+=2)peak=Math.Max(peak,Math.Abs((int)BitConverter.ToInt16(b,i)));return peak>100;}
 static void Main(string[] args){string root=args[0],cache=Path.Combine(root,"test-cache-"+Guid.NewGuid().ToString("N"));
 using(var voice=new VoiceService(root,cache)){
 Test("complete portable voice assets are available",()=>Check(voice.IsAvailable,"voice unavailable"));
 Test("Japanese short word produces non-silent PCM and reuses cache",()=>{string a=voice.PrepareAsync("こんにちは。","../unsafe-key").Result;Check(Valid(a),"no valid speech: "+voice.LastError);Check(Path.GetFullPath(a).StartsWith(Path.GetFullPath(cache)+Path.DirectorySeparatorChar),"cache escaped designated folder");DateTime t=File.GetLastWriteTimeUtc(a);string b=voice.PrepareAsync("こんにちは。","../unsafe-key").Result;Check(a==b&&File.GetLastWriteTimeUtc(b)==t,"cache was regenerated");});
 Test("Stop cancels a pending word",()=>{var t=voice.PrepareAsync("いっしょににほんごをべんきょうしましょう。","cancel-"+Guid.NewGuid());Thread.Sleep(50);voice.Stop();Check(t.Result==null,"stale request returned audio");});
 Test("replacement cancels older request",()=>{var old=voice.PrepareAsync("ながいことばをよみあげています。","old-"+Guid.NewGuid());Thread.Sleep(30);var current=voice.PrepareAsync("ねこ。","cat");Check(old.Result==null,"old request survived");Check(Valid(current.Result),"new request failed: "+voice.LastError);});
 Test("two service instances safely share the same cache key",()=>{using(var prefetch=new VoiceService(root,cache)){var a=voice.PrepareAsync("コーヒー。","shared");var b=prefetch.PrepareAsync("コーヒー。","shared");Check(Valid(a.Result)&&Valid(b.Result)&&a.Result==b.Result,"parallel cache conflict");}});
 Test("long vowels, gemination and contracted kana synthesize",()=>Check(Valid(voice.PrepareAsync("コーヒー。きょう。きって。がっこう。でんわ。りょこう。","kana-regression").Result),"kana generation failed: "+voice.LastError));
 Test("empty input is rejected without crashing",()=>Check(voice.PrepareAsync("  ","empty").Result==null,"empty produced audio"));
 Test("corrupt cache is regenerated",()=>{var p=voice.PrepareAsync("ねこ。","cat").Result;File.WriteAllText(p,"broken");Check(Valid(voice.PrepareAsync("ねこ。","cat").Result),"corrupt audio reused");});
 }
 using(var missing=new VoiceService(Path.Combine(root,"missing-assets"),cache)){Test("missing assets report failure without fallback",()=>Check(!missing.IsAvailable&&missing.PrepareAsync("ねこ。","none").Result==null&&missing.LastError.Length>0,"missing assets hidden"));}
 Console.WriteLine(passed+" passed, "+failed+" failed");Environment.ExitCode=failed==0?0:1;
 }
}