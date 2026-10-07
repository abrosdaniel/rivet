package dev.abros.rivet.client;
import java.util.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
/** Bounded navigation memory, discarded on disconnect or permission changes. */
final class UiNavigation {
 private static final Map<String,Screen> pages=new LinkedHashMap<>();
 private static Screen outside;
 private static final Deque<Screen> history=new ArrayDeque<>();
 private static boolean returning,forward;
 static boolean mainPage(Screen screen,Screen parent){return (parent==null||pages.containsValue(screen))&&(screen instanceof CommunityScreen menu&&menu.itemId.isEmpty()||screen instanceof TaskScreen tasks&&tasks.personal()||screen instanceof FeatureListScreen list&&list.navigationPage()||screen instanceof ServerMenuScreen);}
 static UiActions.Command exitCommand(Screen screen,Screen parent){return mainPage(screen,parent)?UiActions.Command.CLOSE:UiActions.Command.BACK;}
 static void back(Screen from,Screen parent){
  if(mainPage(from,parent)){history.clear();boolean wasReturning=returning;returning=true;try{var mc=Minecraft.getInstance();mc.setScreen(mc.level!=null?null:outside);}finally{returning=wasReturning;}return;}

  Screen next=parent;
  if(next==null){while(!history.isEmpty()&&history.peekLast()==from)history.removeLast();if(!history.isEmpty())next=history.removeLast();}
  else if(history.contains(next)){while(history.peekLast()!=next)history.removeLast();history.removeLast();}
  boolean wasReturning=returning;returning=true;
  try{Minecraft.getInstance().setScreen(next);}finally{returning=wasReturning;}
 }
 static void clear(){pages.clear();history.clear();MenuSidebar.clear();outside=null;UiKeyboard.clear();}
 static void open(Screen from,String key){boolean wasForward=forward;forward=true;try{var mc=Minecraft.getInstance();
  if(from instanceof TaskEditScreen editor&&!editor.leavePage(key))return;
  if(key.equals("notifications")){if(from instanceof CommunityScreen menu&&!menu.leavePage())return;if(from instanceof TaskScreen tasks){if(tasks.pendingMutation())return;tasks.cancelRead();}mc.setScreen(new NotificationPopup(from));return;}
  if(key.equals("tasks")&&!TaskScreen.available())return;
  if(from instanceof CommunityScreen menu&&!menu.leavePage()||from instanceof TaskScreen tasks&&!tasks.leavePage()||from instanceof CommunityForm form&&!form.leavePage())return;
  if(from instanceof FeatureListScreen list)list.leavePage();
  if(from instanceof CommunityScreen menu&&menu.itemId.isEmpty())pages.put(menu.section,from);else if(from instanceof TaskScreen tasks&&tasks.personal())pages.put("tasks",from);else if(from instanceof FeatureListScreen list&&list.navigationPage())pages.put("players",from);
  Screen next=pages.get(key);if(next==null){if(key.equals("players")){FeatureListScreen.open(null,"players");next=mc.screen;}else{next=key.equals("tasks")?new TaskScreen(null,"",""):Set.of("help","admin").contains(key)?new ServerMenuScreen(null,key):new CommunityScreen(null,key,"");mc.setScreen(next);}pages.put(key,next);}else{mc.setScreen(next);if(next instanceof CommunityScreen menu)menu.invalidate();else if(next instanceof TaskScreen tasks)tasks.invalidate(tasks.personal()?"home":"groups","");else if(next instanceof FeatureListScreen list)list.invalidate();}
 }finally{forward=wasForward;}
 }
 static void opening(ScreenEvent.Opening event){
  var current=Minecraft.getInstance().screen;
  UiKeyboard.remember(current);
  if(returning)return;
  if(!forward&&event.getNewScreen()!=null&&history.contains(event.getNewScreen())){while(history.peekLast()!=event.getNewScreen())history.removeLast();history.removeLast();return;}
  if(UiSettingsShell.owns(current)&&UiSettingsShell.owns(event.getNewScreen()))return;
  if(UiTheme.owns(event.getNewScreen())){
   if(!UiTheme.owns(current)){outside=current;history.clear();}
   else if(!forward&&current!=event.getNewScreen()){history.addLast(current);while(history.size()>32)history.removeFirst();}
  }
 }
 static boolean handle(Screen screen,int key){
  if(!UiTheme.owns(screen))return false;
  if(key==256){if(screen instanceof ScrollScreen scroll&&scroll.cancelInteraction())return true;screen.onClose();return true;}
  if(key==259&&!(screen.getFocused() instanceof EditBox)&&!(screen.getFocused() instanceof MultiLineEditBox)){screen.onClose();return true;}
  return false;
 }
 static void key(ScreenEvent.KeyPressed.Pre event){if(UiKeyboard.tab(event.getScreen(),event.getKeyCode(),event.getModifiers())||handle(event.getScreen(),event.getKeyCode()))event.setCanceled(true);}
}
