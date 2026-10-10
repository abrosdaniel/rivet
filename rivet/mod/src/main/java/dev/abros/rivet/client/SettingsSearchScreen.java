package dev.abros.rivet.client;
import java.util.*;
import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Local settings index: navigation opens the matching row in the original workspace. */
final class SettingsSearchScreen extends ScrollScreen {
 private final Screen parent;private final boolean mapOnly;private UiDialog dialog;private String query="";private long changed;private List<SettingsCatalog.Entry> results=List.of();
 SettingsSearchScreen(Screen parent){this(parent,false);}
 SettingsSearchScreen(Screen parent,boolean mapOnly){super(Client.tr("ui.search_settings_c64fa265"));this.parent=parent;this.mapOnly=mapOnly;}
 private List<SettingsCatalog.Entry> matching(){return search(query).stream().filter(e->!mapOnly||e.section()==5||e.section()==7).toList();}
 Screen parentScreen(){return parent;}
 static List<SettingsCatalog.Entry> entries(){return SettingsCatalog.entries();}
 static List<SettingsCatalog.Entry> search(String query){var words=query.strip().toLowerCase(Locale.ROOT).split("\\s+");return entries().stream().filter(e->{String text=(e.name()+" "+UiSettingsShell.SECTIONS.get(e.section())+" "+e.aliases()).toLowerCase(Locale.ROOT);return Arrays.stream(words).allMatch(text::contains);}).toList();}
 private void search(){results=matching();resetScroll();rebuildWidgets();}
 private Screen resultParent(){if(parent instanceof MapSettingsScreen settings)return settings.parentScreen();if(parent instanceof GeneralSettingsScreen settings)return settings.parentScreen();if(parent instanceof AccessibilityScreen settings)return settings.parentScreen();if(parent instanceof HudSettingsScreen settings)return settings.parentScreen();if(parent instanceof SocialSettingsScreen settings)return settings.parentScreen();return parent;}
 @Override protected void init(){dialog=mapOnly?UiSettingsShell.module(width,height,-1,this,this::addRenderableWidget,MapSettingsScreen.categories(),n->minecraft.setScreen(new MapSettingsScreen(resultParent(),n)),()->{},()->{query="";search();},this::onClose):UiSettingsShell.build(width,height,-1,resultParent(),this,this::addRenderableWidget,()->{query="";search();},this::onClose);var body=dialog.body();UiSearchToolbar.query(font,new NativeLayout.Box(body.x(),body.y(),UiSearchToolbar.contentWidth(body.width()),20),Client.text("ui.theme_ping_opacity_23dd1ec2"),query,this::addRenderableWidget,v->{query=v;changed=net.minecraft.Util.getMillis()+120;},this::search,true);if(results.isEmpty()&&query.isBlank())results=matching();var geometry=scrollArea(results.size(),new NativeLayout.Box(body.x(),body.y()+30,body.width(),Math.max(0,body.height()-30)),28);for(int n=firstRow;n<Math.min(results.size(),firstRow+visibleRows);n++){var e=results.get(n);var button=UiActions.button(Component.literal(UiSettingsShell.SECTIONS.get(e.section())+" · "+e.name()),UiActions.Tone.NORMAL,"",v->{var screen=UiSettingsShell.open(e.section()==6?this:resultParent(),e.section());minecraft.setScreen(screen);if(screen instanceof SettingsTarget target)target.revealSetting(e.id());}).bounds(geometry.content().x(),geometry.content().y()+(n-firstRow)*28,geometry.content().width(),24).build();button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(e.name())));addRenderableWidget(button);}}
 @Override public void tick(){if(changed>0&&net.minecraft.Util.getMillis()>=changed){changed=0;search();}}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());if(results.isEmpty())UiEmptyState.draw(g,font,Client.text("ui.no_settings_found_ebc7c2f7"),Client.text("ui.try_ping_opacity_or_a_section_b973ca5d"),dialog.body().x(),dialog.body().y()+32,dialog.body().width(),dialog.body().bottom());});}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){UiNavigation.back(this,parent);}
}
