using System;
using System.Collections.Generic;
using System.Linq;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Web.Script.Serialization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Threading;
using System.Windows.Markup;
using System.Speech.Synthesis;
using Microsoft.Win32;
using Forms = System.Windows.Forms;

namespace Kotoba {
 public static class Program {
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern IntPtr FindWindow(string cls,string title);
  [DllImport("user32.dll")] static extern bool PostMessage(IntPtr h,uint msg,IntPtr w,IntPtr l);
  [STAThread] public static void Main(string[] args) {
   bool firstInstance;
   using(var mutex=new System.Threading.Mutex(true,"Local\\KotobaWindow.Personal",out firstInstance)) {
   if(!firstInstance){var h=FindWindow(null,"小词窗 · 日语随手记");if(h!=IntPtr.Zero)PostMessage(h,0x8001,IntPtr.Zero,IntPtr.Zero);return;}
   var app=new Application();
   app.DispatcherUnhandledException += delegate(object s, DispatcherUnhandledExceptionEventArgs e) {
    MessageBox.Show("遇到一个问题，当前学习进度仍保存在本地。\n"+e.Exception.Message,"小词窗"); e.Handled=true;
   };
   try { app.Run(new MainWindow(args)); }
   catch(Exception ex) { MessageBox.Show("小词窗未能启动：\n"+ex.Message,"小词窗"); }
   }
  }
 }
 public class MainWindow:Window {
  Store store; List<Word> words; Word current; bool revealed; bool practice;
  State undo; string undoId; string view="study"; string notice="";
  Grid shell,body; TextBlock status; Button pin,compact;
  TextBlock term,reading,answer,counts,tag; Button reveal,star; UniformGrid ratings;
  SpeechSynthesizer speech; VoiceService neural,prefetch; string voice; Forms.NotifyIcon tray; HwndSource source;
  DispatcherTimer autoSpeech; int speechVersion;
  bool closed; int[] libraryLevels=null; string query=""; bool starsOnly; ListBox results; TextBlock resultCount,preview;
  Brush ink,muted,purple,pale,line; bool dark; Border frame;
  [DllImport("user32.dll")] static extern bool RegisterHotKey(IntPtr h,int id,uint mods,uint key);
  [DllImport("user32.dll")] static extern bool UnregisterHotKey(IntPtr h,int id);
  string ColorFor(string hex) { if(!dark)return hex; switch(hex.ToUpperInvariant()){case "#24354B":return "#EDF2FF";case "#78859A":return "#AAB8D0";case "#6356D9":return "#8D7CF4";case "#F5F7FC":return "#151D30";case "#E6EAF2":case "#DDE3EE":return "#3B4962";case "#EFF2F8":return "#2A3750";case "#F4F3FE":return "#333451";case "#FAEEEE":return "#4A303D";case "#FCF4E5":return "#4C402D";case "#EAF4F1":return "#24443F";case "#EEEAFB":return "#383151";case "#FFFFFF":return "#202C43";default:return hex;} }
  Brush B(string hex) { return (Brush)new BrushConverter().ConvertFromString(ColorFor(hex)); }
  Brush PanelBrush(string hex,double factor=1) {var c=((SolidColorBrush)B(hex)).Color;if(store!=null&&store.Data.Settings.Transparent)c.A=(byte)Math.Round(255*store.Data.Settings.BackgroundOpacity*factor);return new SolidColorBrush(c);}
  void ApplyAppearance() {dark=store.Data.Settings.Theme=="Dark";ink=B("#24354B");muted=B("#78859A");purple=B("#6356D9");pale=B("#F5F7FC");line=B("#E6EAF2");Foreground=ink;Background=Brushes.Transparent;LoadStyles();if(frame!=null)frame.Background=PanelBrush("#F5F7FC",.45);}
  void RebuildView(){ApplyAppearance();BuildShell();if(view=="study")RenderStudy();else if(view=="library")RenderLibrary();else RenderSettings();}
  Button LevelPicker(int[] selected,Action<int[]> apply,double width) {
   var levels=selected??new[]{5,4,3,2,1};var b=Btn(levels.Length==5?"全部等级 ▾":String.Join("+",levels.OrderByDescending(x=>x).Select(x=>"N"+x))+" ▾",delegate{});b.Width=width;b.FontSize=11;b.Padding=new Thickness(7,7,7,7);b.Margin=new Thickness(0);
   b.Click+=delegate {var menu=new ContextMenu {Background=B("#FFFFFF"),Foreground=ink,BorderBrush=line};var boxes=new List<MenuItem>();foreach(var n in new[]{5,4,3,2,1}){var item=new MenuItem {Header="N"+n,IsCheckable=true,IsChecked=levels.Contains(n),StaysOpenOnClick=true};boxes.Add(item);menu.Items.Add(item);}menu.Items.Add(new Separator());var done=new MenuItem {Header="应用所选等级"};done.Click+=delegate {var chosen=boxes.Where(x=>x.IsChecked).Select(x=>Int32.Parse(x.Header.ToString().Substring(1))).ToArray();if(chosen.Length==0){Toast("请至少选择一个等级");return;}apply(chosen);};menu.Items.Add(done);b.ContextMenu=menu;menu.PlacementTarget=b;menu.IsOpen=true;};return b;
  }
  bool ApplyLevels(int[] ls) {if(ls==null||ls.Length==0||ls.Any(n=>n<1||n>5)){Toast("请至少选择一个有效等级");return false;}var old=store.Data.Settings.Levels;var oldLevel=store.Data.Settings.Level;store.Data.Settings.Levels=ls.Distinct().OrderByDescending(n=>n).ToArray();store.Data.Settings.Level=store.Data.Settings.Levels[0];if(!Save()){store.Data.Settings.Levels=old;store.Data.Settings.Level=oldLevel;return false;}undo=null;SelectNext();if(view=="settings")RenderSettings();return true;}
  public MainWindow(string[] args) {
   Title="小词窗 · 日语随手记"; Width=452; Height=700; MinWidth=350; MinHeight=510;
   WindowStartupLocation=WindowStartupLocation.CenterScreen;
   FontFamily=new FontFamily("Microsoft YaHei UI"); FontSize=13;
   WindowStyle=WindowStyle.None; AllowsTransparency=true; ResizeMode=ResizeMode.CanResizeWithGrip;
   System.Windows.Shell.WindowChrome.SetWindowChrome(this,new System.Windows.Shell.WindowChrome {CaptionHeight=0,ResizeBorderThickness=new Thickness(6),GlassFrameThickness=new Thickness(0),CornerRadius=new CornerRadius(16)});
   var dataPath=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"portable-data");
   var dataArg=args.FirstOrDefault(x=>x.StartsWith("--data="));
   if(dataArg!=null) dataPath=dataArg.Substring(7);
   try { Directory.CreateDirectory(dataPath); var test=Path.Combine(dataPath,"write-check.tmp");File.WriteAllText(test,"");File.Delete(test); }
   catch {dataPath=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"KotobaWindow");notice="程序目录不可写，进度已保存到本机用户目录。";}
   store=new Store(dataPath);neural=new VoiceService(AppDomain.CurrentDomain.BaseDirectory,Path.Combine(dataPath,"voice-cache"));prefetch=new VoiceService(AppDomain.CurrentDomain.BaseDirectory,Path.Combine(dataPath,"voice-cache"));ApplyAppearance();
   if(!String.IsNullOrEmpty(store.Warning)) notice=store.Warning;
   using(var stream=Assembly.GetExecutingAssembly().GetManifestResourceStream("vocab.json")) {
    if(stream==null) throw new Exception("找不到随程序附带的词库。");
    using(var reader=new StreamReader(stream)) words=new JavaScriptSerializer {MaxJsonLength=50000000}.Deserialize<List<Word>>(reader.ReadToEnd());
   }
   words=words.Where(w=>!String.IsNullOrWhiteSpace(w.Id)&&!String.IsNullOrWhiteSpace(w.Term)&&!String.IsNullOrWhiteSpace(w.Meaning)&&w.Level>=1&&w.Level<=5).GroupBy(w=>w.Id).Select(g=>g.First()).ToList();
   if(words.Count==0) throw new Exception("词库没有可用词条。");
   Topmost=store.Data.Settings.Topmost;
   if(store.Data.Settings.Compact){Width=365;Height=540;}
   try {speech=new SpeechSynthesizer();var v=speech.GetInstalledVoices().FirstOrDefault(x=>x.Enabled && x.VoiceInfo.Culture.TwoLetterISOLanguageName=="ja"); if(v!=null){voice=v.VoiceInfo.Name;speech.SelectVoice(voice);} } catch {voice=null;}
   BuildShell(); SelectNext();
   SourceInitialized+=delegate {source=HwndSource.FromHwnd(new WindowInteropHelper(this).Handle);source.AddHook(Hook);if(!RegisterHotKey(source.Handle,1,0x4003,0x4A))Toast("全局快捷键被占用，可通过托盘打开。");};
   Loaded+=delegate {CreateTray();AutoRead();};
   IsVisibleChanged+=delegate {if(!IsVisible)StopSpeech();};
   Closing+=delegate {closed=true;StopSpeech();Save();if(source!=null)UnregisterHotKey(source.Handle,1);if(tray!=null)tray.Dispose();if(speech!=null)speech.Dispose();if(neural!=null)neural.Dispose();if(prefetch!=null)prefetch.Dispose();};
   PreviewKeyDown+=OnKey;
   var timer=new DispatcherTimer {Interval=TimeSpan.FromSeconds(15)};
   timer.Tick+=delegate {if(view=="study" && current==null && IsVisible)SelectNext();};timer.Start();
  }
  void LoadStyles() {
   var x=@"<ResourceDictionary xmlns='http://schemas.microsoft.com/winfx/2006/xaml/presentation' xmlns:x='http://schemas.microsoft.com/winfx/2006/xaml'>
    <Style TargetType='Button'><Setter Property='Foreground' Value='#24354B'/><Setter Property='Background' Value='#EFF2F8'/><Setter Property='BorderThickness' Value='0'/><Setter Property='Padding' Value='12,9'/><Setter Property='Cursor' Value='Hand'/><Setter Property='FocusVisualStyle' Value='{x:Null}'/><Setter Property='Template'><Setter.Value><ControlTemplate TargetType='Button'><Border x:Name='bd' CornerRadius='10' Background='{TemplateBinding Background}' Padding='{TemplateBinding Padding}'><ContentPresenter HorizontalAlignment='Center' VerticalAlignment='Center'/></Border><ControlTemplate.Triggers><Trigger Property='IsMouseOver' Value='True'><Setter TargetName='bd' Property='Opacity' Value='.8'/></Trigger><Trigger Property='IsPressed' Value='True'><Setter TargetName='bd' Property='Opacity' Value='.6'/></Trigger><Trigger Property='IsEnabled' Value='False'><Setter TargetName='bd' Property='Opacity' Value='.35'/></Trigger></ControlTemplate.Triggers></ControlTemplate></Setter.Value></Setter></Style>
    <Style TargetType='TextBox'><Setter Property='Padding' Value='10,8'/><Setter Property='BorderBrush' Value='#DDE3EE'/><Setter Property='BorderThickness' Value='1'/><Setter Property='Background' Value='White'/><Setter Property='VerticalContentAlignment' Value='Center'/></Style>
    <Style TargetType='ComboBox'><Setter Property='Padding' Value='8,6'/><Setter Property='MinHeight' Value='32'/><Setter Property='VerticalContentAlignment' Value='Center'/></Style>
    <Style TargetType='CheckBox'><Setter Property='Margin' Value='0,9,0,9'/><Setter Property='VerticalContentAlignment' Value='Center'/></Style>
    <Style TargetType='ListBoxItem'><Setter Property='Padding' Value='10,10'/><Setter Property='Margin' Value='0,2'/><Setter Property='HorizontalContentAlignment' Value='Stretch'/></Style>
   </ResourceDictionary>";
   foreach(var hex in new[]{"#24354B","#EFF2F8","#DDE3EE"})x=x.Replace(hex,ColorFor(hex));x=x.Replace("Value='White'","Value='"+ColorFor("#FFFFFF")+"'");
   x=x.Replace("<Style TargetType='CheckBox'>","<Style TargetType='CheckBox'><Setter Property='Foreground' Value='"+ColorFor("#24354B")+"'/>");
   x=x.Replace("<Style TargetType='TextBox'>","<Style TargetType='TextBox'><Setter Property='Foreground' Value='"+ColorFor("#24354B")+"'/><Setter Property='CaretBrush' Value='"+ColorFor("#24354B")+"'/>");
   x=x.Replace("<Style TargetType='ListBoxItem'>","<Style TargetType='ListBoxItem'><Setter Property='Foreground' Value='"+ColorFor("#24354B")+"'/>");
   Resources=(ResourceDictionary)XamlReader.Parse(x);
   Resources[SystemColors.WindowBrushKey]=B("#FFFFFF");Resources[SystemColors.WindowTextBrushKey]=ink;Resources[SystemColors.ControlBrushKey]=B("#EFF2F8");Resources[SystemColors.ControlTextBrushKey]=ink;
   Resources[SystemColors.HighlightBrushKey]=purple;Resources[SystemColors.HighlightTextBrushKey]=Brushes.White;
  }
  TextBlock T(string s,double size=13,Brush color=null) {return new TextBlock {Text=s,FontSize=size,Foreground=color??ink,TextWrapping=TextWrapping.Wrap};}
  Button Btn(string s,Action click,bool primary=false) {var b=new Button {Content=s,Margin=new Thickness(3),Background=primary?purple:PanelBrush("#EFF2F8"),Foreground=primary?Brushes.White:ink};b.Click+=delegate {click();};return b;}
  void Row(Grid grid,UIElement e,int r) {Grid.SetRow(e,r);grid.Children.Add(e);}
  void BuildShell() {
   shell=new Grid {Margin=new Thickness(18,12,18,10)};
   shell.RowDefinitions.Add(new RowDefinition {Height=GridLength.Auto});shell.RowDefinitions.Add(new RowDefinition {Height=GridLength.Auto});shell.RowDefinitions.Add(new RowDefinition());shell.RowDefinitions.Add(new RowDefinition {Height=GridLength.Auto});
   var top=new Grid {Margin=new Thickness(2,0,0,12)};top.ColumnDefinitions.Add(new ColumnDefinition());top.ColumnDefinitions.Add(new ColumnDefinition {Width=GridLength.Auto});
   var brand=new StackPanel {Orientation=Orientation.Horizontal,VerticalAlignment=VerticalAlignment.Center};
   var logo=new Border {Background=purple,CornerRadius=new CornerRadius(9),Width=33,Height=33,Child=new TextBlock {Text="あ",Foreground=Brushes.White,FontFamily=new FontFamily("Yu Gothic"),FontSize=23,HorizontalAlignment=HorizontalAlignment.Center,VerticalAlignment=VerticalAlignment.Center}};brand.Children.Add(logo);
   brand.Children.Add(new TextBlock {Text="小词窗",FontSize=17,FontWeight=FontWeights.SemiBold,Margin=new Thickness(9,0,0,0),VerticalAlignment=VerticalAlignment.Center});
   top.Children.Add(brand);
   brand.MouseLeftButtonDown+=delegate(object s,MouseButtonEventArgs e){if(e.ClickCount==2)ToggleCompact();else DragMove();};
   top.MouseLeftButtonDown+=delegate(object s,MouseButtonEventArgs e){if(e.OriginalSource==top)DragMove();};top.Background=Brushes.Transparent;
   var controls=new StackPanel {Orientation=Orientation.Horizontal};Grid.SetColumn(controls,1);top.Children.Add(controls);
   pin=Btn(Topmost?"钉住":"置顶",delegate {Topmost=!Topmost;store.Data.Settings.Topmost=Topmost;pin.Content=Topmost?"钉住":"置顶";Save();});pin.FontSize=11;pin.Padding=new Thickness(8,7,8,7);pin.ToolTip="始终显示在其他窗口上方";controls.Children.Add(pin);
   compact=Btn(store.Data.Settings.Compact?"展开":"小窗",ToggleCompact);compact.FontSize=11;compact.Padding=new Thickness(8,7,8,7);controls.Children.Add(compact);
   var hide=Btn("—",delegate {StopSpeech();Hide();});hide.Padding=new Thickness(8,7,8,7);hide.ToolTip="收起到托盘 · Ctrl+Alt+J 唤出";controls.Children.Add(hide);
   var close=Btn("×",delegate {Close();});close.Padding=new Thickness(8,7,8,7);close.ToolTip="保存并退出";controls.Children.Add(close);
   Row(shell,top,0);
   var nav=new UniformGrid {Columns=3,Margin=new Thickness(0,0,0,10)};
   foreach(var item in new[]{new[]{"study","背单词"},new[]{"library","词库"},new[]{"settings","设置"}}) {var key=item[0];nav.Children.Add(Btn(item[1],delegate {if(key!="study")StopSpeech();view=key;BuildShell();if(key=="study"){if(current==null)SelectNext();else {RenderStudy();AutoRead();}}else if(key=="library")RenderLibrary();else RenderSettings();},view==key));}
   Row(shell,nav,1);body=new Grid();Row(shell,body,2);
   status=T(notice,10,muted);status.Margin=new Thickness(3,9,3,0);status.MaxHeight=30;Row(shell,status,3);frame=new Border {Background=PanelBrush("#F5F7FC",.45),CornerRadius=new CornerRadius(16),BorderBrush=line,BorderThickness=new Thickness(1),Child=shell};Content=frame;
  }
  void ToggleCompact() {
   store.Data.Settings.Compact=!store.Data.Settings.Compact;Width=store.Data.Settings.Compact?365:452;Height=store.Data.Settings.Compact?540:700;compact.Content=store.Data.Settings.Compact?"展开":"小窗";Save();
   if(view=="study")RenderStudy();
  }
  void Toast(string s) {notice=s;if(status!=null)status.Text=s;}
  bool Save() {try {store.Save();return true;}catch(Exception ex){Toast("保存失败："+ex.Message);return false;}}
  Progress P(Word w) {Progress p;return w!=null&&store.Data.Progress.TryGetValue(w.Id,out p)?p:null;}
  void SelectNext() {StopSpeech();practice=false;revealed=false;current=Scheduler.Queue(words,store.Data,DateTime.Now).FirstOrDefault();if(view=="study")RenderStudy();AutoRead();}
  void RenderStudy() {
   body.Children.Clear();var g=new Grid();g.RowDefinitions.Add(new RowDefinition {Height=GridLength.Auto});g.RowDefinitions.Add(new RowDefinition());g.RowDefinitions.Add(new RowDefinition {Height=GridLength.Auto});g.RowDefinitions.Add(new RowDefinition {Height=GridLength.Auto});body.Children.Add(g);
   var meta=new Grid {Margin=new Thickness(2,0,2,12)};meta.ColumnDefinitions.Add(new ColumnDefinition {Width=GridLength.Auto});meta.ColumnDefinitions.Add(new ColumnDefinition());
   meta.Children.Add(LevelPicker(Scheduler.SelectedLevels(store.Data.Settings),delegate(int[] selection){ApplyLevels(selection);},132));
   var day=DateTime.Now.ToString("yyyy-MM-dd");int done=store.Data.DailyReviews.ContainsKey(day)?store.Data.DailyReviews[day]:0;
   var due=words.Count(w=>Scheduler.IncludesLevel(store.Data.Settings,w.Level)&&P(w)!=null&&P(w).Reviews>0&&P(w).Due<=DateTime.Now);
   counts=T("今日 "+done+" 次  ·  待复习 "+due,11,muted);counts.HorizontalAlignment=HorizontalAlignment.Right;counts.VerticalAlignment=VerticalAlignment.Center;Grid.SetColumn(counts,1);meta.Children.Add(counts);Row(g,meta,0);
   var card=new Border {Background=PanelBrush("#FFFFFF",.7),CornerRadius=new CornerRadius(18),BorderBrush=line,BorderThickness=new Thickness(1),Padding=new Thickness(store.Data.Settings.Compact?16:23)};Row(g,card,1);
   var cg=new Grid();cg.RowDefinitions.Add(new RowDefinition {Height=GridLength.Auto});cg.RowDefinitions.Add(new RowDefinition());cg.RowDefinitions.Add(new RowDefinition {Height=GridLength.Auto});card.Child=cg;
   var bar=new DockPanel();tag=T(current==null?"ALL CLEAR":practice?"自由查看 · 不计复习":(P(current)==null||P(current).Reviews==0?"NEW WORD / 新词":"REVIEW / 复习"),10,purple);tag.VerticalAlignment=VerticalAlignment.Center;
   star=Btn(current!=null&&P(current)!=null&&P(current).Star?"★":"☆",ToggleStar);star.FontSize=19;star.Padding=new Thickness(7,1,7,1);star.Background=Brushes.Transparent;star.ToolTip="收藏 F";star.IsEnabled=current!=null;DockPanel.SetDock(star,Dock.Right);bar.Children.Add(star);bar.Children.Add(tag);Row(cg,bar,0);
   var sv=new ScrollViewer {VerticalScrollBarVisibility=ScrollBarVisibility.Auto,HorizontalScrollBarVisibility=ScrollBarVisibility.Disabled,VerticalContentAlignment=VerticalAlignment.Center,Margin=new Thickness(0,14,0,12)};
   var content=new StackPanel {VerticalAlignment=VerticalAlignment.Center};sv.Content=content;Row(cg,sv,1);
   term=T(current==null?"这一轮完成了":current.Term,store.Data.Settings.Compact?34:46);term.FontFamily=new FontFamily("Yu Gothic, Microsoft YaHei UI");term.FontWeight=FontWeights.Medium;term.TextAlignment=TextAlignment.Center;content.Children.Add(term);
   reading=T(current==null?"让记忆休息一下，再回来复习。":(store.Data.Settings.ShowReading||revealed?current.Reading:""),store.Data.Settings.Compact?15:18,muted);reading.FontFamily=new FontFamily("Yu Gothic, Microsoft YaHei UI");reading.TextAlignment=TextAlignment.Center;reading.Margin=new Thickness(0,9,0,14);content.Children.Add(reading);
   answer=T(current==null?EmptyText():revealed?current.Meaning:"先在心里回忆它的意思",store.Data.Settings.Compact?15:17,revealed?ink:muted);answer.TextAlignment=TextAlignment.Center;answer.Margin=new Thickness(0,8,0,0);content.Children.Add(answer);
   if(revealed&&current!=null&&!String.IsNullOrWhiteSpace(current.Note)){var note=T(current.Note,11,muted);note.Margin=new Thickness(0,14,0,0);note.TextAlignment=TextAlignment.Center;content.Children.Add(note);}
   if(revealed&&current!=null&&!String.IsNullOrWhiteSpace(current.Example)){var examples=new StackPanel();examples.Children.Add(T(current.Example,13));var translation=T(current.ExampleMeaning,11,muted);translation.Margin=new Thickness(0,6,0,0);examples.Children.Add(translation);content.Children.Add(new Expander {Header="例句与翻译",Content=examples,Margin=new Thickness(0,15,0,0),FontSize=11,Foreground=muted});}
   var foot=new DockPanel();var audio=Btn("听发音  R",Speak);audio.FontSize=11;audio.Padding=new Thickness(10,6,10,6);audio.Background=B("#F4F3FE");audio.IsEnabled=current!=null;DockPanel.SetDock(audio,Dock.Left);foot.Children.Add(audio);
   var hint=T("N"+(current==null?store.Data.Settings.Level:current.Level)+"  ·  日语 → 中文",10,muted);hint.HorizontalAlignment=HorizontalAlignment.Right;hint.VerticalAlignment=VerticalAlignment.Center;foot.Children.Add(hint);Row(cg,foot,2);
   reveal=Btn(current==null?"去词库逛逛":practice?"回到复习":revealed?"按记忆程度选择下次复习时间":"显示答案  Space",delegate {if(current==null){view="library";BuildShell();RenderLibrary();}else if(practice)SelectNext();else Flip();},!revealed);reveal.Margin=new Thickness(0,12,0,5);reveal.IsEnabled=!revealed||practice||current==null;Row(g,reveal,2);
   var bottom=new StackPanel();ratings=new UniformGrid {Columns=4,Margin=new Thickness(-3,0,-3,0)};
   var labels=new[]{"忘记","困难","记得","轻松"};
   for(int i=1;i<=4;i++){var rating=i;var bp=new StackPanel();bp.Children.Add(new TextBlock {Text=i+"  "+labels[i-1],HorizontalAlignment=HorizontalAlignment.Center,FontWeight=FontWeights.SemiBold});bp.Children.Add(new TextBlock {Text=Scheduler.Preview(P(current),i),HorizontalAlignment=HorizontalAlignment.Center,FontSize=10,Margin=new Thickness(0,4,0,0)});var button=Btn("",delegate {Grade(rating);});button.Content=bp;button.Padding=new Thickness(3,10,3,10);button.IsEnabled=current!=null&&revealed&&!practice;button.Background=B(i==1?"#FAEEEE":i==2?"#FCF4E5":i==3?"#EAF4F1":"#EEEAFB");ratings.Children.Add(button);}
   bottom.Children.Add(ratings);var undoButton=Btn("↶ 撤销上一次评分",Undo);undoButton.IsEnabled=undo!=null;undoButton.FontSize=10;undoButton.Background=Brushes.Transparent;undoButton.Padding=new Thickness(5,5,5,5);bottom.Children.Add(undoButton);Row(g,bottom,3);
   if(String.IsNullOrWhiteSpace(notice))status.Text="Space 翻面 · 1–4 评分 · Ctrl+Alt+J 唤出";
  }
  string EmptyText() {
   var selected=words.Where(w=>Scheduler.IncludesLevel(store.Data.Settings,w.Level)).ToList();
   var next=selected.Where(w=>P(w)!=null&&P(w).Reviews>0&&P(w).Due>DateTime.Now).Select(w=>P(w).Due).OrderBy(x=>x).FirstOrDefault();
   int fresh=selected.Count(w=>P(w)==null||P(w).Reviews==0);
   return (next!=default(DateTime)?"下次复习："+next.ToString("MM-dd HH:mm")+"\n":"")+(fresh>0?"今日新词额度用完了。可在设置调整，\n也可以去词库自由查看。":"所选等级词语都已学过，等待到期复习。") ;
  }
  void Flip() {if(current==null||revealed)return;revealed=true;RenderStudy();}
  void Grade(int rating) {
   if(current==null||!revealed||practice)return;
   undo=store.Data.Clone();undoId=current.Id;Scheduler.Grade(current,store.Data,rating,DateTime.Now);
   if(!Save()){store.Data=undo;undo=null;return;}
   notice="已保存 · "+current.Term+" · "+new[]{"","稍后重学","继续巩固","记住了","很轻松"}[rating];SelectNext();
  }
  void Undo() {if(undo==null)return;StopSpeech();var old=store.Data;undo.Settings=store.Data.Settings;store.Data=undo;if(!Save()){store.Data=old;return;}current=words.FirstOrDefault(x=>x.Id==undoId);undo=null;revealed=true;practice=false;Toast("已撤销评分和本次计数");view="study";BuildShell();RenderStudy();AutoRead();}
  void ToggleStar() {if(current==null)return;ToggleWordStar(current);star.Content=P(current)!=null&&P(current).Star?"★":"☆";}
  void ToggleWordStar(Word w) {var before=store.Data.Clone();var p=P(w);if(p==null){p=new Progress {Id=w.Id};store.Data.Progress[w.Id]=p;}p.Star=!p.Star;undo=null;if(!Save())store.Data=before;else Toast(p.Star?"已加入收藏":"已取消收藏");}
  void StopSpeech() {speechVersion++;if(neural!=null)neural.Stop();if(prefetch!=null)prefetch.Stop();if(autoSpeech!=null)autoSpeech.Stop();if(speech!=null)try{speech.SpeakAsyncCancelAll();}catch{} }
  void AutoRead() {if(closed||!IsVisible||view!="study"||current==null||!store.Data.Settings.AutoSpeak)return;if(autoSpeech!=null)autoSpeech.Stop();int version=speechVersion;string id=current.Id;autoSpeech=new DispatcherTimer {Interval=TimeSpan.FromMilliseconds(350)};autoSpeech.Tick+=delegate {autoSpeech.Stop();if(!closed&&IsVisible&&view=="study"&&current!=null&&current.Id==id&&version==speechVersion&&store.Data.Settings.AutoSpeak)Speak();};autoSpeech.Start();}
  string SpeechText(Word w) {return (String.IsNullOrWhiteSpace(w.Reading)?w.Term:w.Reading).Trim().Trim('〜','～','~').Trim();}
  async void PrepareNext() {if(closed||!IsVisible||view!="study"||practice||current==null||!store.Data.Settings.AutoSpeak||store.Data.Settings.VoiceBackend!="Neural"||prefetch==null||!prefetch.IsAvailable)return;var next=Scheduler.Queue(words,store.Data,DateTime.Now).FirstOrDefault(w=>w.Id!=current.Id);if(next!=null)try {await prefetch.PrepareAsync(SpeechText(next),next.Id);}catch{} }
  async void Speak() {
   if(current==null||closed)return;
   StopSpeech();int version=speechVersion;string id=current.Id,name=current.Term;
   string text=SpeechText(current);
   if(store.Data.Settings.VoiceBackend=="Neural") {
    if(neural==null||!neural.IsAvailable){Toast("离线语音文件不完整，请完整解压新版压缩包，或在设置选 Windows 备用。");return;}
    Toast("正在准备发音 · "+name);
    bool ok=await neural.PlayAsync(text,id);
    if(closed||version!=speechVersion||current==null||current.Id!=id)return;
    Toast(ok?"神经语音 · "+name:neural.LastError);if(ok)PrepareNext();return;
   }
   if(String.IsNullOrEmpty(voice)){Toast("未检测到 Windows 日语语音，可在设置切回离线神经语音。");return;}
   try {speech.SpeakAsync(text);Toast("Windows 语音 · "+name);}catch(Exception ex){Toast("发音未成功："+ex.Message);}
  }
  void RenderLibrary() {
   body.Children.Clear();var g=new Grid();foreach(var h in new[]{GridLength.Auto,GridLength.Auto,new GridLength(1,GridUnitType.Star),GridLength.Auto})g.RowDefinitions.Add(new RowDefinition {Height=h});body.Children.Add(g);
   var search=new TextBox {Text=query,Margin=new Thickness(2,0,2,8),ToolTip="输入日语、假名或中文"};search.TextChanged+=delegate {query=search.Text;RefreshLibrary();};Row(g,search,0);
   var filters=new DockPanel {Margin=new Thickness(2,0,2,7)};var lev=LevelPicker(libraryLevels,delegate(int[] ls){libraryLevels=ls;RenderLibrary();},145);filters.Children.Add(lev);
   var fav=new CheckBox {Content="只看收藏",IsChecked=starsOnly,Margin=new Thickness(12,0,0,0),VerticalAlignment=VerticalAlignment.Center};fav.Click+=delegate {starsOnly=fav.IsChecked==true;RefreshLibrary();};filters.Children.Add(fav);Row(g,filters,1);
   results=new ListBox {Background=PanelBrush("#FFFFFF",.7),BorderBrush=line,BorderThickness=new Thickness(1)};VirtualizingStackPanel.SetIsVirtualizing(results,true);ScrollViewer.SetHorizontalScrollBarVisibility(results,ScrollBarVisibility.Disabled);
   results.SelectionChanged+=delegate {var item=results.SelectedItem as ListBoxItem;if(item==null)return;var w=item.Tag as Word;preview.Text=w.Term+"  "+w.Reading+"\n"+w.Meaning;};results.MouseDoubleClick+=delegate {OpenSelected();};Row(g,results,2);
   var foot=new StackPanel {Margin=new Thickness(2,8,2,0)};resultCount=T("",10,muted);foot.Children.Add(resultCount);preview=T("搜索汉字、假名或中文。双击词条可打开卡片。",12);preview.Margin=new Thickness(0,9,0,5);preview.MaxHeight=90;foot.Children.Add(preview);
   var buttons=new UniformGrid {Columns=2};buttons.Children.Add(Btn("查看卡片",OpenSelected,true));buttons.Children.Add(Btn("收藏 / 取消",delegate {var item=results.SelectedItem as ListBoxItem;if(item==null){Toast("请先选择一个词条");return;}ToggleWordStar((Word)item.Tag);RefreshLibrary();}));foot.Children.Add(buttons);Row(g,foot,3);RefreshLibrary();
   status.Text="内置 "+words.Count.ToString("N0")+" 词 · 社区 JLPT 分级 · 离线搜索";
  }
  void RefreshLibrary() {
   if(results==null||resultCount==null||view!="library")return;
   var found=words.Where(w=>(libraryLevels==null||libraryLevels.Contains(w.Level))&&(!starsOnly||(P(w)!=null&&P(w).Star))&&(String.IsNullOrWhiteSpace(query)||w.Term.IndexOf(query.Trim(),StringComparison.OrdinalIgnoreCase)>=0||w.Reading.IndexOf(query.Trim(),StringComparison.OrdinalIgnoreCase)>=0||w.Meaning.IndexOf(query.Trim(),StringComparison.OrdinalIgnoreCase)>=0)).ToList();
   results.Items.Clear();foreach(var w in found.Take(200)){var row=new StackPanel();row.Children.Add(T((P(w)!=null&&P(w).Star?"★ ":"")+w.Term+"   "+w.Reading+"   N"+w.Level,14));var meaning=T(w.Meaning,11,muted);meaning.TextWrapping=TextWrapping.NoWrap;meaning.TextTrimming=TextTrimming.CharacterEllipsis;row.Children.Add(meaning);results.Items.Add(new ListBoxItem {Content=row,Tag=w});}
   resultCount.Text="找到 "+found.Count+" 词"+(found.Count>200?" · 显示前 200 条，请输入关键词缩小范围":"");
  }
  void OpenSelected() {var item=results.SelectedItem as ListBoxItem;if(item==null){Toast("请先选择一个词条");return;}StopSpeech();current=(Word)item.Tag;revealed=true;practice=true;view="study";BuildShell();RenderStudy();AutoRead();}
  void RenderSettings() {
   body.Children.Clear();var sv=new ScrollViewer {VerticalScrollBarVisibility=ScrollBarVisibility.Auto,HorizontalScrollBarVisibility=ScrollBarVisibility.Disabled};var p=new StackPanel {Margin=new Thickness(3,2,6,10)};sv.Content=p;body.Children.Add(sv);
   p.Children.Add(T("按自己的节奏来",22));p.Children.Add(new TextBlock {Text="少量新词，及时复习。今天比昨天多一点。",Foreground=muted,Margin=new Thickness(0,7,0,18),TextWrapping=TextWrapping.Wrap});
   var dailyLabel=T("每天新词上限",13);p.Children.Add(dailyLabel);var daily=new ComboBox {ItemsSource=new[]{0,5,10,20,30,50,100},SelectedItem=store.Data.Settings.DailyNew,HorizontalAlignment=HorizontalAlignment.Left,Width=130,Margin=new Thickness(0,7,0,5)};if(daily.SelectedIndex<0){daily.ItemsSource=new[]{0,5,10,20,30,50,100,store.Data.Settings.DailyNew}.Distinct().OrderBy(x=>x).ToList();daily.SelectedItem=store.Data.Settings.DailyNew;}
   daily.SelectionChanged+=delegate {if(daily.SelectedItem==null)return;store.Data.Settings.DailyNew=(int)daily.SelectedItem;undo=null;Save();current=null;};p.Children.Add(daily);p.Children.Add(T("所有等级共用每日额度；0 表示只复习。",11,muted));
   var kana=new CheckBox {Content="翻面前显示假名提示",IsChecked=store.Data.Settings.ShowReading};kana.Click+=delegate {store.Data.Settings.ShowReading=kana.IsChecked==true;Save();};p.Children.Add(kana);
   var top=new CheckBox {Content="窗口保持置顶",IsChecked=Topmost};top.Click+=delegate {Topmost=top.IsChecked==true;store.Data.Settings.Topmost=Topmost;pin.Content=Topmost?"钉住":"置顶";Save();};p.Children.Add(top);
   p.Children.Add(Section("词库与顺序"));p.Children.Add(LevelPicker(Scheduler.SelectedLevels(store.Data.Settings),delegate(int[] ls){ApplyLevels(ls);},180));
   var shuffle=new CheckBox {Content="新词乱序（到期复习仍然优先）",IsChecked=store.Data.Settings.Shuffle};shuffle.Click+=delegate {store.Data.Settings.Shuffle=shuffle.IsChecked==true;undo=null;current=null;Save();};p.Children.Add(shuffle);
   p.Children.Add(Btn("重新洗牌",delegate {store.Data.Settings.Shuffle=true;store.Data.Settings.ShuffleSeed=new Random().Next();undo=null;current=null;Save();Toast("新词顺序已重新打乱");RenderSettings();}));
   p.Children.Add(Section("外观与悬浮"));var themes=new UniformGrid {Columns=2};themes.Children.Add(Btn("亮色",delegate {store.Data.Settings.Theme="Light";Save();RebuildView();},!dark));themes.Children.Add(Btn("暗色",delegate {store.Data.Settings.Theme="Dark";Save();RebuildView();},dark));p.Children.Add(themes);
   var transparent=new CheckBox {Content="透明背景（文字保持清晰）",IsChecked=store.Data.Settings.Transparent};transparent.Click+=delegate {store.Data.Settings.Transparent=transparent.IsChecked==true;Save();RebuildView();};p.Children.Add(transparent);
   var alphaLabel=T("背景浓度 "+Math.Round(store.Data.Settings.BackgroundOpacity*100)+"%",11,muted);p.Children.Add(alphaLabel);
   var alpha=new Slider {Minimum=.2,Maximum=1,Value=store.Data.Settings.BackgroundOpacity,TickFrequency=.05,IsSnapToTickEnabled=true,Margin=new Thickness(0,8,0,6)};alpha.ValueChanged+=delegate {store.Data.Settings.BackgroundOpacity=alpha.Value;alphaLabel.Text="背景浓度 "+Math.Round(alpha.Value*100)+"%";if(frame!=null)frame.Background=PanelBrush("#F5F7FC",.45);};alpha.PreviewMouseLeftButtonUp+=delegate {Save();};alpha.LostKeyboardFocus+=delegate {Save();};p.Children.Add(alpha);p.Children.Add(T("勾选置顶后，可叠在其他应用上。拖动标题移动位置。",11,muted));
   var auto=new CheckBox {Content="新卡出现时自动发音（R 仍可重播）",IsChecked=store.Data.Settings.AutoSpeak};auto.Click+=delegate {store.Data.Settings.AutoSpeak=auto.IsChecked==true;if(!store.Data.Settings.AutoSpeak)StopSpeech();Save();};p.Children.Add(auto);
   p.Children.Add(Section("发音"));var voices=new UniformGrid {Columns=2};voices.Children.Add(Btn("自然语音 · 离线",delegate {StopSpeech();store.Data.Settings.VoiceBackend="Neural";Save();RenderSettings();},store.Data.Settings.VoiceBackend=="Neural"));voices.Children.Add(Btn("Windows 备用",delegate {StopSpeech();store.Data.Settings.VoiceBackend="Windows";Save();RenderSettings();},store.Data.Settings.VoiceBackend=="Windows"));p.Children.Add(voices);
   p.Children.Add(T(store.Data.Settings.VoiceBackend=="Neural"?(neural.IsAvailable?"Supertonic 3 日语神经语音 · 已就绪":"语音文件缺失，请完整解压 voice 文件夹"):(voice==null?"未检测到 Windows 日语语音":"Windows 语音："+voice),12));
   p.Children.Add(T("自然语音已包含在安装包内，无需联网或语言包。首次遇到一个词需生成几秒，之后直接播放缓存。按词条假名朗读，AI 语音仍可能出现读音或声调偏差。",11,muted));
   p.Children.Add(Section("进度与备份"));var backup=new UniformGrid {Columns=2};backup.Children.Add(Btn("导出进度",Export));backup.Children.Add(Btn("导入进度",Import));p.Children.Add(backup);p.Children.Add(T("每次评分自动保存；覆盖前保留上一份备份。导入将替换当前进度。",11,muted));
   var path=T(store.FilePath,10,muted);path.Margin=new Thickness(0,7,0,0);p.Children.Add(path);
   p.Children.Add(Section("快捷键"));p.Children.Add(T("Space  显示答案       1–4  评分\nR  听发音       F  收藏\nCtrl+Z  撤销评分\nCtrl+Alt+J  全局显示 / 隐藏\n拖动顶部标题移动窗口；拖动边缘调整大小。",12));
   p.Children.Add(Section("词库与来源"));p.Children.Add(T(String.Join("  ·  ",Enumerable.Range(1,5).Reverse().Select(l=>"N"+l+" "+words.Count(w=>w.Level==l)))+"\n合计 "+words.Count+" 个词条",12));p.Children.Add(T("采用社区整理的 N5–N1 分级与中文释义，不是 JLPT 官方完整考试词表。词义可能需要结合语境核对。具体来源、署名和许可见程序文件夹中的“词库来源与许可.md”。词库 © egg rolls，CC BY-NC 4.0，仅限非商业使用。",11,muted));
   p.Children.Add(Section("小词窗 1.1"));p.Children.Add(T("本地优先 · 无账号 · 无广告\n采用简单间隔复习算法，参考 Anki / jpdb / Mochi 的学习操作设计；并非 FSRS。\n关闭窗口会保存并退出；“—”收起到托盘。",11,muted));
  }
  TextBlock Section(string s){var t=T(s,15);t.FontWeight=FontWeights.SemiBold;t.Margin=new Thickness(0,22,0,9);return t;}
  void Export() {var d=new SaveFileDialog {Filter="学习进度 JSON|*.json",FileName="小词窗进度-"+DateTime.Now.ToString("yyyyMMdd")+".json"};if(d.ShowDialog(this)!=true)return;try {store.Export(d.FileName);Toast("进度已导出");}catch(Exception ex){Toast("导出失败："+ex.Message);}}
  void Import() {var d=new OpenFileDialog {Filter="学习进度 JSON|*.json"};if(d.ShowDialog(this)!=true)return;if(MessageBox.Show(this,"导入会替换当前学习进度。建议先导出一份备份。是否继续？","导入进度",MessageBoxButton.YesNo,MessageBoxImage.Question)!=MessageBoxResult.Yes)return;try {store.Import(d.FileName);undo=null;current=null;ApplyAppearance();Topmost=store.Data.Settings.Topmost;Width=store.Data.Settings.Compact?365:452;Height=store.Data.Settings.Compact?540:700;Toast("进度已导入");view="study";BuildShell();SelectNext();}catch(Exception ex){Toast("导入失败，原进度保留："+ex.Message);}}
  void OnKey(object sender,KeyEventArgs e) {
   if(Keyboard.FocusedElement is TextBox || Keyboard.FocusedElement is ComboBox)return;
   if(Keyboard.Modifiers==ModifierKeys.Control&&e.Key==Key.Z){Undo();e.Handled=true;return;}
   if(view!="study"||Keyboard.Modifiers!=ModifierKeys.None)return;
   if(e.Key==Key.Space){Flip();e.Handled=true;}else if(e.Key==Key.R){Speak();e.Handled=true;}else if(e.Key==Key.F){ToggleStar();e.Handled=true;}
   else if(e.Key>=Key.D1&&e.Key<=Key.D4){Grade((int)e.Key-(int)Key.D1+1);e.Handled=true;}
   else if(e.Key>=Key.NumPad1&&e.Key<=Key.NumPad4){Grade((int)e.Key-(int)Key.NumPad1+1);e.Handled=true;}
  }
  void CreateTray() {
   tray=new Forms.NotifyIcon {Text="小词窗 · Ctrl+Alt+J 唤出",Visible=true,Icon=System.Drawing.Icon.ExtractAssociatedIcon(Assembly.GetExecutingAssembly().Location)??System.Drawing.SystemIcons.Application};
   var menu=new Forms.ContextMenuStrip();menu.Items.Add("显示小词窗",null,delegate {Dispatcher.Invoke(new Action(ShowWindow));});menu.Items.Add("退出",null,delegate {Dispatcher.Invoke(new Action(Close));});tray.ContextMenuStrip=menu;tray.DoubleClick+=delegate {Dispatcher.Invoke(new Action(ShowWindow));};
  }
  void ShowWindow(){if(closed)return;Show();WindowState=WindowState.Normal;Activate();AutoRead();}
  IntPtr Hook(IntPtr hwnd,int msg,IntPtr wParam,IntPtr lParam,ref bool handled){if(msg==0x8001){ShowWindow();handled=true;}if(msg==0x0312&&wParam.ToInt32()==1){if(IsVisible)Hide();else ShowWindow();handled=true;}return IntPtr.Zero;}
 }
}
