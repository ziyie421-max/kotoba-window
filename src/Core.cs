using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;
using System.Web.Script.Serialization;

namespace Kotoba {
 public class Word { public string Id,Term,Reading,Meaning,Note,Example,ExampleMeaning; public int Level; }
 public class Progress {
  public string Id; public int Reviews,Lapses; public double Interval;
  public DateTime Due,FirstSeen; public bool Star;
 }
 public class Settings {
  public int Level=5,DailyNew=20;
  public int[] Levels=null;
  public bool Shuffle=false,AutoSpeak=true,Transparent=false;
  public int ShuffleSeed=Guid.NewGuid().GetHashCode();
  public string Theme="Light",VoiceBackend="Neural";
  public double BackgroundOpacity=.88;
  public bool Topmost=true,Compact=false,ShowReading=true;
 }
 public class State {
  public int Version=1;
  public Settings Settings=new Settings();
  public Dictionary<string,Progress> Progress=new Dictionary<string,Progress>();
  public Dictionary<string,int> DailyReviews=new Dictionary<string,int>();
  public Dictionary<string,int> DailyNew=new Dictionary<string,int>();
  public State Clone() { return DeepClone(this); }
  public static State DeepClone(State source) {
   if(source==null) throw new ArgumentNullException("source");
   var s=new State(); s.Version=source.Version;
   s.Settings=new Settings {Level=source.Settings.Level,DailyNew=source.Settings.DailyNew,Topmost=source.Settings.Topmost,Compact=source.Settings.Compact,ShowReading=source.Settings.ShowReading,Levels=source.Settings.Levels==null?null:(int[])source.Settings.Levels.Clone(),Shuffle=source.Settings.Shuffle,ShuffleSeed=source.Settings.ShuffleSeed,AutoSpeak=source.Settings.AutoSpeak,Theme=source.Settings.Theme,VoiceBackend=source.Settings.VoiceBackend,Transparent=source.Settings.Transparent,BackgroundOpacity=source.Settings.BackgroundOpacity};
   foreach(var kv in source.Progress) {var p=kv.Value;s.Progress.Add(kv.Key,new Progress{Id=p.Id,Reviews=p.Reviews,Lapses=p.Lapses,Interval=p.Interval,Due=p.Due,FirstSeen=p.FirstSeen,Star=p.Star});}
   s.DailyReviews=new Dictionary<string,int>(source.DailyReviews);
   s.DailyNew=new Dictionary<string,int>(source.DailyNew); return s;
  }
 }
 public static class Scheduler {
  public static int[] SelectedLevels(Settings settings) {
   if(settings==null)throw new ArgumentNullException("settings");
   var levels=settings.Levels==null?new[]{settings.Level}:settings.Levels;
   if(levels.Length==0||levels.Length>5||levels.Any(x=>x<1||x>5)||levels.Distinct().Count()!=levels.Length)throw new ArgumentException("请至少选择一个有效且不重复的 N1–N5 等级。");
   return (int[])levels.Clone();
  }
  // Stable across process restarts; String.GetHashCode is intentionally not used.
  public static uint StableOrder(string id,int seed) {
   unchecked {uint h=2166136261u^(uint)seed;foreach(char c in id){h^=c;h*=16777619u;}h^=h>>16;h*=0x85ebca6bu;h^=h>>13;h*=0xc2b2ae35u;h^=h>>16;return h;}
  }
  public static bool IncludesLevel(Settings settings,int level) { return SelectedLevels(settings).Contains(level); }
  static string Day(DateTime now) {return now.ToString("yyyy-MM-dd",CultureInfo.InvariantCulture);}
  public static List<Word> Queue(List<Word> words,State state,DateTime now) {
   if(words==null||state==null)throw new ArgumentNullException();
   int used;state.DailyNew.TryGetValue(Day(now),out used);
   int left=Math.Max(0,state.Settings.DailyNew-used);
   var due=new List<Word>();var fresh=new List<Word>();var ids=new HashSet<string>();
   var levels=new HashSet<int>(SelectedLevels(state.Settings));
   foreach(var w in words) {
    if(w==null||!levels.Contains(w.Level)||String.IsNullOrWhiteSpace(w.Id)||!ids.Add(w.Id))continue;
    Progress p;
    if(!state.Progress.TryGetValue(w.Id,out p)||p.Reviews==0) {fresh.Add(w);}
    else if(p.Due<=now) due.Add(w);
   }
   due=due.OrderBy(w=>state.Progress[w.Id].Due).ThenBy(w=>w.Id,StringComparer.Ordinal).ToList();
   // Shuffle the entire eligible new-word pool BEFORE applying the remaining quota.
   if(state.Settings.Shuffle)fresh=fresh.OrderBy(w=>StableOrder(w.Id,state.Settings.ShuffleSeed)).ThenBy(w=>w.Id,StringComparer.Ordinal).ToList();
   due.AddRange(fresh.Take(left));return due;
  }
  static double Interval(Progress p,int rating) {
   if(rating<1||rating>4)throw new ArgumentOutOfRangeException("rating");
   bool isNew=p==null||p.Reviews==0;
   double previous=isNew?0:p.Interval;
   if(rating==1)return 1.0/1440;
   if(rating==2)return Math.Min(36500,Math.Max(.25,previous*1.2));
   if(rating==3)return isNew?1:Math.Min(36500,Math.Max(1,previous*2.5));
   return isNew?4:Math.Min(36500,Math.Max(4,previous*3));
  }
  public static void Grade(Word word,State state,int rating,DateTime now) {
   if(word==null||state==null)throw new ArgumentNullException();
   if(String.IsNullOrWhiteSpace(word.Id))throw new ArgumentException("词条缺少 ID。");
   if(rating<1||rating>4)throw new ArgumentOutOfRangeException("rating");
   Progress p;state.Progress.TryGetValue(word.Id,out p);
   double interval=Interval(p,rating);
   if(p==null) {p=new Progress{Id=word.Id};state.Progress.Add(word.Id,p);}
   bool first=p.Reviews==0&&p.FirstSeen==DateTime.MinValue;
   if(p.Reviews==0&&p.FirstSeen==DateTime.MinValue)p.FirstSeen=now;
   if(rating==1&&p.Reviews>0)p.Lapses++;
   p.Reviews++;p.Interval=interval;p.Due=now.AddDays(interval);
   string key=Day(now);int count;state.DailyReviews.TryGetValue(key,out count);state.DailyReviews[key]=count+1;
   if(first) {state.DailyNew.TryGetValue(key,out count);state.DailyNew[key]=count+1;}
  }
  public static string Preview(Progress progress,int rating) {
   double days=Interval(progress,rating);
   if(days<1.0/24)return Math.Max(1,(int)Math.Round(days*1440)).ToString()+" 分钟";
   if(days<1)return Math.Round(days*24,1).ToString("0.#",CultureInfo.InvariantCulture)+" 小时";
   return Math.Round(days,1).ToString("0.#",CultureInfo.InvariantCulture)+" 天";
  }
 }
 public class Store {
  const long MaxBytes=32*1024*1024;
  public State Data;
  public string FilePath;
  public string Warning;
  readonly string directory;
  public Store(string directory) {
   if(String.IsNullOrWhiteSpace(directory))throw new ArgumentException("保存目录不能为空。");
   this.directory=Path.GetFullPath(directory);Directory.CreateDirectory(this.directory);
   FilePath=Path.Combine(this.directory,"progress.json");Data=new State();Load();
  }
  static JavaScriptSerializer Serializer() {return new JavaScriptSerializer {MaxJsonLength=(int)MaxBytes,RecursionLimit=80};}
  static void Require(bool condition,string message) {if(!condition)throw new InvalidDataException(message);}
  static void ValidateCounters(Dictionary<string,int> counters) {
   Require(counters!=null&&counters.Count<=100000,"每日统计格式不正确。");
   foreach(var c in counters) {DateTime date;Require(DateTime.TryParseExact(c.Key,"yyyy-MM-dd",CultureInfo.InvariantCulture,DateTimeStyles.None,out date),"统计日期格式不正确。");Require(c.Value>=0&&c.Value<=10000000,"统计数值超出范围。");}
  }
  static void Validate(State s) {
   Require(s!=null&&s.Version==1,"不支持此进度文件版本。");
   Require(s.Settings!=null,"文件缺少设置。");
   Require(s.Settings.Level>=1&&s.Settings.Level<=5,"JLPT 等级必须为 N1 至 N5。");
   Require(s.Settings.Levels==null||(s.Settings.Levels.Length>=1&&s.Settings.Levels.Length<=5&&s.Settings.Levels.All(x=>x>=1&&x<=5)&&s.Settings.Levels.Distinct().Count()==s.Settings.Levels.Length),"请选择至少一个有效且不重复的 JLPT 等级。");
   Require(s.Settings.Theme=="Light"||s.Settings.Theme=="Dark","主题配置不正确。");
   Require(s.Settings.VoiceBackend=="Neural"||s.Settings.VoiceBackend=="Windows","语音后端配置不正确。");
   Require(!Double.IsNaN(s.Settings.BackgroundOpacity)&&!Double.IsInfinity(s.Settings.BackgroundOpacity)&&s.Settings.BackgroundOpacity>=.2&&s.Settings.BackgroundOpacity<=1,"背景透明度必须在 20% 至 100% 之间。");
   Require(s.Settings.DailyNew>=0&&s.Settings.DailyNew<=1000,"每日新词数量超出范围。");
   Require(s.Progress!=null&&s.Progress.Count<=100000,"学习记录格式不正确。");
   foreach(var kv in s.Progress) {
    var p=kv.Value;Require(p!=null&&!String.IsNullOrWhiteSpace(kv.Key)&&kv.Key.Length<=300&&p.Id==kv.Key,"词条记录或 ID 不正确。");
    Require(p.Reviews>=0&&p.Reviews<=10000000&&p.Lapses>=0&&p.Lapses<=p.Reviews,"复习次数超出范围。");
    Require(!Double.IsNaN(p.Interval)&&!Double.IsInfinity(p.Interval)&&p.Interval>=0&&p.Interval<=36500,"复习间隔超出范围。");
    Require(p.Reviews==0||(p.Due>=new DateTime(2000,1,1)&&p.Due<=new DateTime(2400,1,1)),"复习日期超出范围。");
    Require(p.FirstSeen==DateTime.MinValue||(p.FirstSeen>=new DateTime(2000,1,1)&&p.FirstSeen<=new DateTime(2300,1,1)),"首次学习日期超出范围。");
   }
   ValidateCounters(s.DailyReviews);ValidateCounters(s.DailyNew);
  }
  static State Read(string path) {
   Require(new FileInfo(path).Length<=MaxBytes,"进度文件过大。");
   try {
    string json=File.ReadAllText(path,Encoding.UTF8);var serializer=Serializer();
    var root=serializer.DeserializeObject(json) as Dictionary<string,object>;
    Require(root!=null&&root.ContainsKey("Version")&&root.ContainsKey("Settings")&&root.ContainsKey("Progress")&&root.ContainsKey("DailyReviews")&&root.ContainsKey("DailyNew"),"这不是完整的小词窗进度文件。");
    Require(root["Settings"] is Dictionary<string,object> && root["Progress"] is Dictionary<string,object> && root["DailyReviews"] is Dictionary<string,object> && root["DailyNew"] is Dictionary<string,object>,"进度文件结构不正确。");
    var settings=(Dictionary<string,object>)root["Settings"];
    Require(settings.ContainsKey("Level")&&settings.ContainsKey("DailyNew"),"设置中缺少等级或新词数量。");
    var state=serializer.Deserialize<State>(json);
    // JavaScriptSerializer reads JSON dates as UTC. Queue compares against local DateTime.Now.
    if(state!=null&&state.Progress!=null)foreach(var p in state.Progress.Values) {
     if(p==null)continue;
     if(p.Due!=DateTime.MinValue&&p.Due.Kind==DateTimeKind.Utc)p.Due=p.Due.ToLocalTime();
     if(p.FirstSeen!=DateTime.MinValue&&p.FirstSeen.Kind==DateTimeKind.Utc)p.FirstSeen=p.FirstSeen.ToLocalTime();
    }
    Validate(state);return state;
   } catch(InvalidDataException) {throw;} catch(Exception e) {throw new InvalidDataException("无法读取进度文件："+e.Message,e);}
  }
  static void AtomicWrite(string path,State state) {
   Validate(state);
   string full=Path.GetFullPath(path);string dir=Path.GetDirectoryName(full);Directory.CreateDirectory(dir);
   string temp=full+".tmp-"+Guid.NewGuid().ToString("N");
   try {
    byte[] bytes=new UTF8Encoding(false).GetBytes(Serializer().Serialize(state));
    using(var stream=new FileStream(temp,FileMode.CreateNew,FileAccess.Write,FileShare.None)) {stream.Write(bytes,0,bytes.Length);stream.Flush(true);}
    if(File.Exists(full))File.Replace(temp,full,full+".bak",true);else File.Move(temp,full);
   } finally {if(File.Exists(temp))File.Delete(temp);}
  }
  public void Load() {
   Warning=null;
   if(!File.Exists(FilePath)) {
    if(File.Exists(FilePath+".bak")) {try{Data=Read(FilePath+".bak");Warning="主进度文件缺失，已从备份恢复。";AtomicWrite(FilePath,Data);return;}catch(Exception e){Warning="备份无法读取："+e.Message;}}
    Data=new State();return;
   }
   try {Data=Read(FilePath);return;}catch(Exception e){Warning="进度文件损坏："+e.Message;}
   bool preserved=false;
   try {
    string corrupt=FilePath+".corrupt-"+DateTime.Now.ToString("yyyyMMddHHmmssfff")+"-"+Guid.NewGuid().ToString("N").Substring(0,6);
    File.Copy(FilePath,corrupt,false);preserved=true;
   } catch(Exception e) {Warning+=" 损坏文件暂时无法另存："+e.Message;}
   if(File.Exists(FilePath+".bak")) {
    try {
     var recovered=Read(FilePath+".bak");Data=recovered;
     Warning+=" 已从上一份备份恢复学习进度。";
     // A locked/unwritable primary must not discard a valid backup from memory.
     if(preserved) {
      string recovery=FilePath+".recovery-"+Guid.NewGuid().ToString("N");
      try {AtomicWrite(recovery,recovered);File.Replace(recovery,FilePath,null,true);}
      catch(Exception e) {Warning+=" 主文件暂时无法修复："+e.Message;}
      finally {try {if(File.Exists(recovery))File.Delete(recovery);}catch {}}
     }
     return;
    } catch(Exception e) {Warning+=" 备份无法读取："+e.Message;}
   }
   Data=new State();Warning+=" 已使用空白进度；原始文件仍保留，请先检查备份。";
  }
  public void Save() {AtomicWrite(FilePath,Data);}
  public void Export(string path) {AtomicWrite(path,Data);}
  public void Import(string path) {var candidate=Read(path);AtomicWrite(FilePath,candidate);Data=candidate;Warning=null;}
 }
}
