package dev.abros.rivet.client;
import dev.abros.rivet.core.pack.PackManifest;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Optional components belong to the server publication, not a separate catalog. */
final class ServerPackScreen extends ScrollScreen {
 private final Screen parent;private final PackManifest manifest;private final Set<String> selected;private final Consumer<Set<String>> apply;private UiDialog dialog;
 ServerPackScreen(Screen parent,PackManifest manifest,Set<String> selected,Consumer<Set<String>> apply){super(Component.literal("Сборка сервера"));this.parent=parent;this.manifest=manifest;this.selected=new HashSet<>(selected);this.apply=apply;}
 protected void init(){dialog=UiDialog.fit(width,height,460,320);var body=dialog.body();var area=scrollArea(manifest.components().size(),body,42);for(int i=firstRow;i<Math.min(manifest.components().size(),firstRow+visibleRows);i++){var c=manifest.components().get(i);int y=body.y()+(i-firstRow)*42;var button=UiActions.button(Component.literal((selected.contains(c.id())?"✓ ":"○ ")+c.name()),UiActions.Tone.NORMAL,"",b->{if(!selected.remove(c.id()))selected.add(c.id());rebuildWidgets();}).bounds(area.content().x(),y,area.content().width(),20).build();button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(c.description())));addRenderableWidget(button);}var f=dialog.footer();UiActions.row(new dev.abros.rivet.core.NativeLayout.Box(f.x(),f.bottom()-20,f.width(),20),this::addRenderableWidget,UiActions.primary("Проверить изменения",()->apply.accept(Set.copyOf(selected)),true),UiActions.action("Отмена",this::onClose,true));}
 public void renderBackground(GuiGraphics g,int x,int y,float d){var f=dialog.frame();UiDialog.surface(g,f.x(),f.y(),f.width(),f.height());}
 public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);var h=dialog.header();UiHeading.dialog(g,font,title,h.x(),dialog.frame().y(),h.width());if(manifest.components().isEmpty()){var b=dialog.body();g.drawWordWrap(font,Component.literal("Все файлы этой сборки обязательны."),b.x(),b.y(),b.width(),UiPalette.color(0xEEEEEE));}});}
 public void onClose(){minecraft.setScreen(parent);}public boolean isPauseScreen(){return false;}
}
