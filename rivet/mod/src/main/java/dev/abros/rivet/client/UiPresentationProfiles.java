package dev.abros.rivet.client;
import java.util.List;
/** Client presentation only: never alters permissions, chat modes or server configuration. */
final class UiPresentationProfiles {
 static void apply(int profile){if(profile<0||profile>2)throw new IllegalArgumentException("Unknown profile");AccessibilityScreen.applyProfile(profile);var hud=HudSettings.INSTANCE;var social=SocialSettings.INSTANCE;hud.enabled=true;hud.opacity=profile==1?.65f:.82f;hud.hidden.clear();hud.profileHidden.clear();hud.profileHidden.addAll(List.of("session","ping"));if(profile==0)hud.hidden.addAll(List.of("groups","unread"));if(profile==1){hud.hidden.addAll(List.of("task","event","groups","unread"));hud.profileHidden.addAll(List.of("prefix","suffix"));}social.hints=profile!=1;social.hintOpacity=.95;social.density=profile==2?2:0;social.columnWidth=profile==2?220:190;social.tabOpacity=profile==1?.45:.65;social.heads=true;social.ping=false;social.footer=profile!=1;hud.save();social.save();RivetHud.refresh();}
 static int current(){
  var hud=HudSettings.INSTANCE;var social=SocialSettings.INSTANCE;
  for(int p=0;p<3;p++){
   var hidden=p==0?java.util.Set.of("groups","unread"):p==1?java.util.Set.of("task","event","groups","unread"):java.util.Set.of();
   var profileHidden=p==1?java.util.Set.of("session","ping","prefix","suffix"):java.util.Set.of("session","ping");
   if(AccessibilityScreen.density()==(p==0?0:p==1?1:2)&&AccessibilityScreen.animations()==(p!=1)&&AccessibilityScreen.motionMillis()==120
    &&hud.enabled&&Math.abs(hud.opacity-(p==1?.65f:.82f))<.0001&&hud.hidden.equals(hidden)&&hud.profileHidden.equals(profileHidden)
    &&social.hints==(p!=1)&&Math.abs(social.hintOpacity-.95)<.0001&&social.density==(p==2?2:0)&&social.columnWidth==(p==2?220:190)
    &&Math.abs(social.tabOpacity-(p==1?.45:.65))<.0001&&social.heads&&!social.ping&&social.footer==(p!=1))return p;
  }
  return -1;
 }
 private UiPresentationProfiles(){}
}
