package dev.abros.rivet.client;
import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Compound action footer. Widths and gaps are computed once for every host. */
final class UiActions {
 static final int CONTROL_HEIGHT=UiKit.CONTROL_HEIGHT, COMMAND_WIDTH=96, GAP=UiKit.SPACE_SMALL+2;
 enum Command {
  REFRESH("server.refresh",UiIcons.REFRESH), RETRY("retry",UiIcons.REFRESH), BACK("back",""), CLOSE("close",""), LIST("backToList","");
  final String label,icon;Command(String label,String icon){this.label=label;this.icon=icon;}
 }
 private static final Map<Button,Command> commands=new WeakHashMap<>();
 static Button command(Command command,NativeLayout.Box slot,Consumer<AbstractWidget> add,Runnable run){
  var b=button(Client.tr(command.label),Tone.NORMAL,command.icon,ignored->run.run()).bounds(slot.x(),slot.y(),Math.min(COMMAND_WIDTH,slot.width()),CONTROL_HEIGHT).build();
  commands.put(b,command);add.accept(b);return b;
 }
 static Command commandOf(Button button){return commands.get(button);}
 enum Tone { NORMAL, PRIMARY, DANGER }
 record Style(Tone tone,String icon){}
 private static final java.util.Set<Button> tools=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
 private static final java.util.Map<Button,Integer> iconColors=new java.util.WeakHashMap<>();
 static Button tool(Component label,String icon,int x,int y,Runnable run){return tool(label,icon,x,y,w->run.run());}
 static Button tool(Component label,String icon,int x,int y,Button.OnPress press){var b=button(label,Tone.NORMAL,icon,press).bounds(x,y,20,20).build();tools.add(b);b.setTooltip(Tooltip.create(label));return b;}
 static boolean isTool(AbstractButton button){return tools.contains(button);}
 static void iconColor(Button button,int color){iconColors.put(button,color);}
 static int iconColor(AbstractButton button,int fallback){return iconColors.getOrDefault(button,fallback);}
 private static final Map<Button,Style> styles=new WeakHashMap<>();
 record Action(Component label,Runnable run,boolean enabled,Tone tone,String icon,String reason){Action withIcon(String icon){return new Action(label,run,enabled,tone,icon,reason);}Action because(String reason){return new Action(label,run,enabled,tone,icon,reason);}}
 static Action action(String label,Runnable run,boolean enabled){return new Action(Component.literal(label),run,enabled,Tone.NORMAL,"","");}
 static Button.Builder button(Component label,Tone tone,String icon,Button.OnPress press){return new Button.Builder(label,press){@Override public Button build(){var built=style(super.build(),tone,icon);if(net.minecraft.client.Minecraft.getInstance().font.width(label)>built.getWidth()-16)built.setTooltip(Tooltip.create(label));return built;}};}
 static Action primary(String label,Runnable run,boolean enabled){return new Action(Component.literal(label),run,enabled,Tone.PRIMARY,UiIcons.CHECK,"");}
 static Action danger(String label,Runnable run,boolean enabled){return new Action(Component.literal(label),run,enabled,Tone.DANGER,UiIcons.DELETE,"");}
 static <T extends Button> T style(T button,Tone tone,String icon){styles.put(button,new Style(tone,icon));return button;}
 static Style style(net.minecraft.client.gui.components.AbstractButton button){return styles.getOrDefault(button,new Style(Tone.NORMAL,""));}
 static List<Button> row(NativeLayout.Box area,Consumer<AbstractWidget> add,Action... actions){
  return row(area,add,Arrays.stream(actions).map(a->NativeLayout.Track.flex(1)).toArray(NativeLayout.Track[]::new),actions);
 }
 static List<Button> row(NativeLayout.Box area,Consumer<AbstractWidget> add,NativeLayout.Track[] tracks,Action... actions){
  if(tracks.length!=actions.length)throw new IllegalArgumentException("Action tracks must match actions");
  var boxes=NativeLayout.row(area,6,tracks);
  int natural=Arrays.stream(actions).mapToInt(a->net.minecraft.client.Minecraft.getInstance().font.width(a.label())+24).sum()+6*Math.max(0,actions.length-1);
  if(actions.length>1&&area.height()>=44&&natural>area.width()){
   int columns=(actions.length+1)/2;var flow=new ArrayList<NativeLayout.Box>();
   for(int i=0;i<actions.length;i++)flow.add(new NativeLayout.Box(area.x()+(i%columns)*(area.width()+6)/columns,area.y()+i/columns*24,(area.width()-6*(columns-1))/columns,CONTROL_HEIGHT));boxes=flow;
  }
  var widgets=new ArrayList<Button>();
  for(int i=0;i<actions.length;i++){var a=actions[i];var b=boxes.get(i);var widget=Button.builder(a.label(),ignored->a.run().run()).bounds(b.x(),b.y(),b.width(),CONTROL_HEIGHT).build();style(widget,a.tone(),a.icon());if(net.minecraft.client.Minecraft.getInstance().font.width(a.label())>b.width()-16)widget.setTooltip(Tooltip.create(a.label()));widget.active=a.enabled();if(!a.enabled()&&!a.reason().isBlank())widget.setTooltip(Tooltip.create(Component.literal(a.reason())));add.accept(widget);widgets.add(widget);}
  return List.copyOf(widgets);
 }
 static Button close(NativeLayout.Box area,Consumer<AbstractWidget> add,Runnable close){int w=Math.min(COMMAND_WIDTH,area.width());return command(Command.CLOSE,new NativeLayout.Box(area.right()-w,area.y(),w,CONTROL_HEIGHT),add,close);}
 private UiActions(){}
}
