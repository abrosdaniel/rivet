package dev.abros.rivet.client;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.util.*;
/** Client-only palette. Player colours, item textures and success/error semantics stay intact. */
final class UiPalette {
 private record Palette(String id,String name,int surface,int accent,int secondary){}
 private static final List<Palette> THEMES=List.of(
  new Palette("golden-dark","Golden Dark",0x24282B,0xE2BE75,0x83C6C4),
  new Palette("love-pink","Love Pink",0xCBBEC4,0x622B46,0x4B3448),
  new Palette("obsidian","Obsidian",0x211D32,0xB9A3F2,0x978BDD),
  new Palette("create-stuff","Create Stuff",0x302922,0xE4B57A,0x88BBD9),
  new Palette("mine-main","Mine Main",0x282B25,0xC6DAA3,0x83B06A),
  new Palette("midnight-blue","Midnight Blue",0x1F2835,0x91B8D1,0x86A3C6),
  new Palette("moss-stone","Moss & Stone",0x29302B,0xA8BF93,0x8BA89E),
  new Palette("copper-ember","Copper Ember",0x302726,0xC99878,0xB8A096),
  new Palette("silver-slate","Silver Slate",0x23272D,0xBAC7D2,0x96B4C4),
  new Palette("deep-teal","Deep Teal",0x1C2B2B,0x86C4B8,0x9AB9D0),
  new Palette("walnut-workshop","Walnut Workshop",0x29231F,0xCBB393,0x8CA8BA),
  new Palette("berry-night","Berry Night",0x2B2029,0xCF9AAA,0xB6A3C7),
  new Palette("ruby-dark","Ruby Dark",0x302328,0xECA0A5,0xD6B190),
  new Palette("sunflower-dark","Sunflower Dark",0x2D2B20,0xE8CE78,0xA8C9A0),
  new Palette("ivory","Ivory",0xE5E0D5,0x665334,0x3F6266),
  new Palette("coral-light","Coral Light",0xE4D3CE,0x8D343D,0x695248),
  new Palette("lemon-light","Lemon Light",0xE7E1BD,0x705720,0x425E48),
  new Palette("sky-light","Sky Light",0xD4DFE8,0x315E89,0x3A6667),
  new Palette("mint-light","Mint Light",0xD3E2D7,0x30624C,0x446679),
  new Palette("lavender-light","Lavender Light",0xDDD6E8,0x604482,0x655162));
 private static final List<String> NAMES=THEMES.stream().map(Palette::name).toList();
 private static boolean loaded;private static int index;private static final Map<Integer,Integer> colors=new HashMap<>();
 private UiPalette(){}
 private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/ui-theme.json");}
 private static void load(){if(loaded)return;loaded=true;try{if(Files.exists(file())){String id=Json.opt(Json.read(file()),"theme","golden-dark");for(int n=0;n<THEMES.size();n++)if(THEMES.get(n).id.equals(id))index=n;}}catch(Exception ignored){}}
 static List<String> names(){return NAMES;}
 static void reload(){loaded=false;index=0;colors.clear();load();}
 static int selected(){load();return index;}
 static String name(){return names().get(selected());}
 static void select(int next)throws java.io.IOException {if(next<0||next>=THEMES.size())throw new IllegalArgumentException("Unknown theme");Json.write(file(),Map.of("theme",THEMES.get(next).id));preview(next);}
 static void preview(int next){if(next<0||next>=THEMES.size())throw new IllegalArgumentException("Unknown theme");loaded=true;index=next;colors.clear();}
 private static boolean light(Palette p){return ((p.surface>>16)&255)+((p.surface>>8)&255)+(p.surface&255)>420;}
 static boolean light(){return light(THEMES.get(selected()));}
 private static int lightText(Palette p){return p.id.equals("love-pink")?0x382431:blend(p.accent,0x111111,.60f);}
 private static int lightMuted(Palette p){return p.id.equals("love-pink")?0x583744:blend(p.accent,0x202020,.25f);}
 static int outline(){var p=THEMES.get(selected());return light()?0xFF000000|blend(p.surface,p.accent,.65f):0xFF000000|blend(p.surface,p.accent,0.60f);}
 static int foundation(int role){var p=THEMES.get(selected());boolean bright=light(p);int rgb=switch(role){case 0->blend(p.surface,bright?0xFFFFFF:0x030507,bright?.48f:.34f);case 1->blend(p.surface,bright?0xFFFFFF:p.surface,bright?.72f:0);case 2->blend(p.surface,bright?0xFFFFFF:0xAAB6C1,bright?.30f:.08f);default->blend(p.surface,bright?0xFFFFFF:0x030507,bright?.85f:.18f);};return 0xFF000000|rgb;}
 static int inputSurface(){return foundation(3); }
 static int insetSurface(){var p=THEMES.get(selected());return light()?0xFF000000|blend(p.surface,p.accent,.13f):color(0xFF102338);}
 static int scrollTrack(){var p=THEMES.get(selected());return 0xFF000000|(light()?blend(p.surface,p.accent,.16f):blend(p.surface,0x030507,0.3f));}
 static int scrollThumb(){var p=THEMES.get(selected());return 0xFF000000|p.accent;}
 static String id(){return THEMES.get(selected()).id;}
 static int color(int original){load();return colors.computeIfAbsent(original,UiPalette::tint);}
 private static int tint(int original){int rgb=original&0xFFFFFF,alpha=original&0xFF000000;var theme=THEMES.get(index);if(rgb==theme.accent||rgb==theme.secondary)return original;
  if(light(theme)&&rgb==0xD5F4EB)return alpha|lightText(theme);
  if(Set.of(0xE2BE75,0xFFD166,0xE7C77B,0xE9C578).contains(rgb))return alpha|theme.accent;
  if(Set.of(0x83C6C4,0x85CFBD,0xD5F4EB,0x253E45,0x324B59,0x283E4A).contains(rgb)){int value=rgb==0xD5F4EB?blend(theme.secondary,0xFFFFFF,0.6f):rgb==0x253E45||rgb==0x324B59||rgb==0x283E4A?blend(theme.surface,theme.secondary,0.24f):theme.secondary;return alpha|value;}
  if(light(theme)){
   if(rgb==lightText(theme)||rgb==lightMuted(theme))return original;
   if(rgb==0x6D3D48)return alpha|blend(theme.surface,theme.accent,.22f);
   // Text may pass through both a component and the shared renderer. Keep resolved colours stable.
   if(Set.of(0x644C12,0x18452E,0x193E5C,0x642B1D,0x4F3061,0x382431,0x583744,0x7E2034,0xA63959,0x965369).contains(rgb))return original;
   if(rgb==0xD5F4EB)return alpha|lightText(theme);
   if(rgb==0xE0BB68)return alpha|0x644C12;
   if(rgb==0xF0A77C)return alpha|0x642B1D;
   if(rgb==0x82B6F2)return alpha|0x193E5C;
   if(rgb==0xB49AE8)return alpha|0x4F3061;
   if(Set.of(0xFFFFFF,0xE0E9EE,0xF2F6F8,0xE7EDF1,0xCCD4DE,0xD7E2EC,0xE0E8F0,0xD8E9F2,0xD8E8F0,0xEEEEEE).contains(rgb))return alpha|lightText(theme);
   if(Set.of(0xA4B5C0,0xBAC7D2,0x99ADB9,0x8FA6B5,0x82909C,0x687580,0xB6C2CC,0x96A6B5,0xAFBFCD,0xBBBBBB,0xBAC6D2,0xBAC9D3,0xCCCCCC,0xC1CED8,0x8DA7B8,0x879BAD,0x91A7B7,0xA9B9C8,0xAEBBC8,0x999999,0xAAAAAA,0xABB5BE).contains(rgb))return alpha|lightMuted(theme);
   if(Set.of(0x79CBA6,0x8CBFA2,0x83C6A3,0x7BC9A5,0x66CC88).contains(rgb))return alpha|0x18452E;
   if(Set.of(0xEF7777,0xDB7777,0xFF7777,0xDC827F,0xDC7777).contains(rgb))return alpha|0x7E2034;
  }
  int red=(rgb>>16)&255,green=(rgb>>8)&255,blue=rgb&255;int max=Math.max(red,Math.max(green,blue)),min=Math.min(red,Math.min(green,blue));
  // Graphite surfaces only: leave red warnings, green success and text colours unchanged.
  if(max>=12&&max<=125&&(blue>=red&&green>=red||max-min<14)){
   float brightness=(red+green+blue)/3f;float base=(((theme.surface>>16)&255)+((theme.surface>>8)&255)+(theme.surface&255))/3f;
   if(light(theme))return alpha|blend(theme.surface,theme.accent,Math.min(0.22f,brightness/380f));
   int result=brightness>=base?blend(theme.surface,0xAAB6C1,Math.min(0.55f,(brightness-base)/150f)):blend(theme.surface,0x030507,Math.min(0.9f,(base-brightness)/base));return alpha|result;
  }return original;
 }
 private static int blend(int a,int b,float t){int out=0;for(int shift=0;shift<=16;shift+=8)out|=((int)(((a>>shift)&255)*(1-t)+((b>>shift)&255)*t))<<shift;return out;}
}
