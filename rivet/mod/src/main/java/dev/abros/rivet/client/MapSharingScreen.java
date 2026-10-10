package dev.abros.rivet.client;
import net.minecraft.client.gui.screens.Screen;
/** Direct entry into the corresponding map settings section. */
final class MapSharingScreen extends MapSettingsScreen {
 MapSharingScreen(Screen parent){super(parent);selectCategory(Math.max(0,categories().indexOf(Client.text("ui.visibility_bd3fe393"))));}
}
