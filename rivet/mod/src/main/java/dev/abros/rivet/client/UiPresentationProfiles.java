package dev.abros.rivet.client;
import java.util.List;
/** Client presentation only: never alters permissions, chat modes or server configuration. */
final class UiPresentationProfiles {
 static void apply(int profile){if(profile<0||profile>2)throw new IllegalArgumentException("Unknown profile");AccessibilityScreen.applyProfile(profile);var hud=HudSettings.INSTANCE;var social=SocialSettings.INSTANCE;hud.enabled=true;hud.scale=1;hud.opacity=profile==1?.65f:.82f;hud.hidden.clear();hud.profileHidden.clear();hud.profileHidden.addAll(List.of("session","ping"));if(profile==0)hud.hidden.addAll(List.of("groups","unread"));if(profile==1){hud.hidden.addAll(List.of("task","event","groups","unread"));hud.profileHidden.addAll(List.of("prefix","suffix"));}social.hints=profile!=1;social.hintOpacity=.95;social.density=profile==2?2:0;social.columnWidth=profile==2?220:190;social.tabOpacity=profile==1?.45:.65;social.heads=true;social.ping=false;social.footer=profile!=1;hud.save();social.save();RivetHud.refresh();}
 private UiPresentationProfiles(){}
}
