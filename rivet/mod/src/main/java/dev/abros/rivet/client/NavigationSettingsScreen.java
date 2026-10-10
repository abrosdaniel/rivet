package dev.abros.rivet.client;
import net.minecraft.client.gui.screens.Screen;
/** Compatibility entry for saved search/navigation actions; settings belong to the map. */
final class NavigationSettingsScreen extends MapSettingsScreen {
 NavigationSettingsScreen(Screen parent){super(parent,3);}
 @Override public void revealSetting(String id){selectCategory(3);revealRow(SettingsCatalog.row(5,id));rebuildWidgets();}
}
