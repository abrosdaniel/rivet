package dev.abros.rivet.client;
import java.util.*;
import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Local settings index: navigation opens the matching row in the original workspace. */
final class SettingsSearchScreen extends ScrollScreen {
 private final Screen parent;private UiDialog dialog;private String query="";private long changed;private List<SettingsCatalog.Entry> results=List.of();
 SettingsSearchScreen(Screen parent){super(Component.literal("Поиск настроек"));this.parent=parent;}
 static List<SettingsCatalog.Entry> entries(){return SettingsCatalog.entries();}
 static List<SettingsCatalog.Entry> search(String query){var words=query.strip().toLowerCase(Locale.ROOT).split("\\s+");return entries().stream().filter(e->{String text=(e.name()+" "+UiSettingsShell.SECTIONS.get(e.section())+" "+e.aliases()).toLowerCase(Locale.ROOT);return Arrays.stream(words).allMatch(text::contains);}).toList();}
 private void search(){results=search(query);resetScroll();rebuildWidgets();}
 @Override protected void init(){dialog=UiSettingsShell.build(width,height,-1,parent,this,this::addRenderableWidget,()->{query="";search();},this::onClose);var body=dialog.body();UiSearchToolbar.query(font,new NativeLayout.Box(body.x(),body.y(),body.width(),20),"Тема, пинг, прозрачность…",query,this::addRenderableWidget,v->{query=v;changed=net.minecraft.Util.getMillis()+120;},this::search,true);if(results.isEmpty()&&query.isBlank())results=search("");var geometry=scrollArea(results.size(),new NativeLayout.Box(body.x(),body.y()+30,body.width(),Math.max(0,body.height()-30)),28);for(int n=firstRow;n<Math.min(results.size(),firstRow+visibleRows);n++){var e=results.get(n);var button=UiActions.button(Component.literal(UiSettingsShell.SECTIONS.get(e.section())+" · "+e.name()),UiActions.Tone.NORMAL,"",v->{var screen=UiSettingsShell.open(e.section()==6?this:parent,e.section());minecraft.setScreen(screen);if(screen instanceof SettingsTarget target)target.revealSetting(e.id());}).bounds(geometry.content().x(),geometry.content().y()+(n-firstRow)*28,geometry.content().width(),24).build();button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(e.name())));addRenderableWidget(button);}}
 @Override public void tick(){if(changed>0&&net.minecraft.Util.getMillis()>=changed){changed=0;search();}}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());if(results.isEmpty())UiEmptyState.draw(g,font,"Настройки не найдены","Попробуйте «пинг», «прозрачность» или название раздела.",dialog.body().x(),dialog.body().y()+32,dialog.body().width(),dialog.body().bottom());});}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){UiNavigation.back(this,parent);}
}
