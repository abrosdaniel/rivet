package dev.abros.rivet.client;

import dev.abros.rivet.core.CommunityLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import java.lang.reflect.*;


/** Isolated World Map camera adapter. Probe before use; restore the user's camera setting on exit. */
final class XaeroMapBridge {
 private static Screen opened;private static Field attached;private static boolean previousAttached;
 static String probe()throws ReflectiveOperationException{
   Class<?> gui=Class.forName("xaero.map.gui.GuiMap"),processor=Class.forName("xaero.map.MapProcessor"),session=Class.forName("xaero.map.WorldMapSession");
   session.getMethod("getCurrentSession");session.getMethod("getMapProcessor");processor.getMethod("isMapWorldUsable");processor.getMethod("getMapWorld").getReturnType().getMethod("getCurrentDimensionId");
   gui.getConstructor(Screen.class,Screen.class,processor,Entity.class);
   field(gui,"cameraX",double.class);field(gui,"cameraZ",double.class);field(gui,"shouldResetCameraPos",boolean.class);
   if(!Modifier.isStatic(field(gui,"attachedCamera",boolean.class).getModifiers()))throw new NoSuchFieldException("attachedCamera must be static");
   return "Xaero World Map · map camera";
 }
 static void open(Screen parent,CommunityLocation place)throws ReflectiveOperationException{
  reset();var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)throw new IllegalArgumentException("Мир ещё не загружен.");
  if(!mc.level.dimension().location().toString().equals(place.dimension()))throw new IllegalArgumentException("Это место в другом измерении. Перейдите в него или сохраните метку через Xaero Minimap.");
  Class<?> sessionType=Class.forName("xaero.map.WorldMapSession"),processorType=Class.forName("xaero.map.MapProcessor"),guiType=Class.forName("xaero.map.gui.GuiMap");
  Object session=sessionType.getMethod("getCurrentSession").invoke(null);if(session==null)throw new IllegalArgumentException("Карта ещё загружается.");
  Object processor=sessionType.getMethod("getMapProcessor").invoke(session);
  if(!(boolean)processorType.getMethod("isMapWorldUsable").invoke(processor))throw new IllegalArgumentException("Карта ещё загружается.");
  Object world=processorType.getMethod("getMapWorld").invoke(processor);
  Object dimension=world.getClass().getMethod("getCurrentDimensionId").invoke(world);
  if(!mc.level.dimension().equals(dimension))throw new IllegalArgumentException("Выберите текущее измерение в Xaero World Map и повторите.");
  // Xaero exposes the screen constructor but no public camera-position method.
  Field cameraX=field(guiType,"cameraX",double.class),cameraZ=field(guiType,"cameraZ",double.class),reset=field(guiType,"shouldResetCameraPos",boolean.class),follow=field(guiType,"attachedCamera",boolean.class);
  var screen=(Screen)guiType.getConstructor(Screen.class,Screen.class,processorType,Entity.class).newInstance(parent,parent,processor,mc.player);
  boolean wasAttached=follow.getBoolean(null);mc.setScreen(screen);
  try{cameraX.setDouble(screen,place.x()+0.5);cameraZ.setDouble(screen,place.z()+0.5);reset.setBoolean(screen,false);follow.setBoolean(null,false);opened=screen;attached=follow;previousAttached=wasAttached;}
  catch(ReflectiveOperationException ex){follow.setBoolean(null,wasAttached);mc.setScreen(parent);throw ex;}
 }
 private static Field field(Class<?> owner,String name,Class<?> expected)throws ReflectiveOperationException{var field=owner.getDeclaredField(name);if(field.getType()!=expected)throw new NoSuchFieldException(name);field.setAccessible(true);return field;}
 static void reset(){restore();}
 private static void restore(){if(opened!=null){try{attached.setBoolean(null,previousAttached);}catch(ReflectiveOperationException failure){com.mojang.logging.LogUtils.getLogger().debug("Rivet: cannot restore Xaero map camera",failure);}finally{opened=null;attached=null;}}}
 static void tick(){if(opened!=null&&Minecraft.getInstance().screen!=opened)restore();}
}
