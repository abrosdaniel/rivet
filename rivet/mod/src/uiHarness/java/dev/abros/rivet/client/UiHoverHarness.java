package dev.abros.rivet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.InputType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Mouse focus must not masquerade as hover; text inputs deliberately keep their editing focus. */
final class UiHoverHarness {
 static void verify(GuiGraphics graphics){var mc=Minecraft.getInstance();var previous=mc.getLastInputType();int checks=0;
  try{for(AbstractWidget w:new AbstractWidget[]{new UiToggle("Toggle",10,10,180,true,()->{}),new UiChoiceRow(10,10,180,"Choice",false,-1,UiKit.accent(),()->{}),new UiMenuItem("Menu",10,10,180,false,false,()->{}),UiActions.button(Component.literal("Setting"),UiActions.Tone.NORMAL,"",b->{}).bounds(10,10,180,20).build()}){
   mc.setLastInputType(InputType.MOUSE);w.setFocused(true);w.render(graphics,-1,-1,0);check(!UiTheme.highlighted(w)&&!UiTheme.keyboardFocus(w),"Mouse click focus stuck");checks++;
   w.render(graphics,15,15,0);check(UiTheme.highlighted(w),"Pointer hover missing");checks++;
   w.render(graphics,-1,-1,0);check(!UiTheme.highlighted(w),"Pointer leave stuck");checks++;
   mc.setLastInputType(InputType.KEYBOARD_TAB);check(UiTheme.highlighted(w)&&UiTheme.keyboardFocus(w),"Keyboard focus missing");checks++;
   Screen parent=new Screen(Component.empty()){};UiTheme.rendering(parent,()->check(!UiTheme.keyboardFocus(w),"Parent focus leaks through modal"));checks++;
   w.active=false;check(!UiTheme.highlighted(w)&&!UiTheme.keyboardFocus(w),"Disabled control highlights");checks++;
  }System.out.println("RIVET_UI_HOVER_OK checks="+checks);}finally{mc.setLastInputType(previous);}
 }
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
