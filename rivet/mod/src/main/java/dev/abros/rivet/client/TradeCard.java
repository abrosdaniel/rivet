package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
/** Read-only offer preview; opening the native container is the only item interaction. */
final class TradeCard extends Button {
 private final JsonObject row;
 TradeCard(int x,int y,int width,JsonObject row,Runnable open){super(x,y,width,84,Component.literal(Json.str(row,"name")).append(" · ").append(Client.tr((row.has("uuidB")&&!Json.str(row,"attention").equals("frozen")?"trade.status.":"trade.card.")+Json.str(row,"attention"))),b->open.run(),DEFAULT_NARRATION);this.row=row;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
  setTooltip(null);var font=Minecraft.getInstance().font;int x=getX(),y=getY();String state=Json.str(row,"attention");boolean attention=state.equals("decision")||state.equals("collect");
  UiKit.plate(g,x,y,width,height,UiTheme.mix(UiKit.surface(UiKit.Surface.CONTROL),UiKit.surface(UiKit.Surface.HOVER),UiTheme.hover(this)));
  if(attention)g.fill(x,y+10,x+2,y+height-10,UiKit.accent());
  PlayerFaceRenderer.draw(g,SkinClient.skin(java.util.UUID.fromString(Json.str(row,"uuid"))),x+10,y+5,24);
  if(row.has("uuidB"))PlayerFaceRenderer.draw(g,SkinClient.skin(java.util.UUID.fromString(Json.str(row,"uuidB"))),x+25,y+13,16);
  if(!row.has("uuidB"))g.fill(x+29,y+24,x+35,y+30,row.get("online").getAsBoolean()?UiPalette.color(0xFF79CBA6):UiKit.muted());
  UiCanvas.text(g,font,UiKit.fit(font,Json.str(row,"name"),width-56),x+44,y+5,UiKit.text(),false);
  UiCanvas.text(g,font,UiKit.fit(font,Client.text((row.has("uuidB")&&!state.equals("frozen")?"trade.status.":"trade.card.")+state),width-56),x+44,y+18,attention?UiKit.accent():UiKit.muted(),false);
  preview(g,"give",x+10,y+31,mx,my);preview(g,"receive",x+10,y+50,mx,my);
  String footer=row.get("status").getAsString().equals("open")||row.get("status").getAsString().equals("draft")?Client.tr("trade.card.expires",java.time.Instant.ofEpochMilli(row.get("expires").getAsLong()).atZone(AccessibilityScreen.zone()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"))).getString():Client.text("trade.status."+row.get("status").getAsString());
  if(my>=y+68&&my<y+height&&mx>=x&&mx<x+width)setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(footer)));
  UiCanvas.text(g,font,UiKit.fit(font,created(row),width-20),x+10,y+70,UiKit.muted(),false);
  if(UiTheme.keyboardFocus(this))UiKit.focus(g,x,y,width,height);
 }
 static String created(JsonObject row){long at=row.has("createdAt")?row.get("createdAt").getAsLong():0;return at>0?Client.tr("trade.created",java.time.Instant.ofEpochMilli(at).atZone(AccessibilityScreen.zone()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).getString():Client.text("trade.created.unknown");}
 private void preview(GuiGraphics g,String side,int x,int y,int mx,int my){var font=Minecraft.getInstance().font;boolean give=side.equals("give");String label=row.has("nameA")?Json.str(row,give?"nameA":"nameB"):Client.text(give?"trade.compact.give":"trade.compact.receive");int offset=Math.min(100,Math.max(font.width(Client.text("trade.compact.give")),font.width(Client.text("trade.compact.receive")))+6);UiCanvas.text(g,font,UiKit.fit(font,label,offset-6),x,y+4,UiKit.muted(),false);
  var icons=row.getAsJsonArray(side);int count=row.get(side+"Count").getAsInt();if(count==0){UiCanvas.text(g,font,UiKit.fit(font,Client.text(row.get((give?"receive":"give")+"Count").getAsInt()>0?"trade.card.free":"trade.card.empty"),Math.max(1,width-20-offset)),x+offset,y+4,UiKit.accent(),false);return;}
  int shown=Math.max(0,Math.min(icons.size(),(width-20-offset-30)/22));
  for(int i=0;i<shown;i++){var icon=icons.get(i).getAsJsonObject();var key=ResourceLocation.tryParse(icon.get("id").getAsString());var stack=key==null?ItemStack.EMPTY:new ItemStack(BuiltInRegistries.ITEM.get(key));int ix=x+offset+i*22;g.renderItem(stack,ix,y);String amount=TradeScreen.compactCount(icon.get("count").getAsInt());g.pose().pushPose();g.pose().translate(ix+17,y+12,200);g.pose().scale(.65f,.65f,1);UiCanvas.text(g,font,amount,-font.width(amount),0,0xFFFFFF,true);g.pose().popPose();
   if(mx>=ix&&mx<ix+20&&my>=y&&my<y+18)setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(icon.get("name").getAsString()+" × "+String.format(java.util.Locale.ROOT,"%,d",icon.get("count").getAsInt()).replace(',',' '))));
  }
  if(count>shown)UiCanvas.text(g,font,"+"+(count-shown),x+offset+shown*22,y+4,UiKit.muted(),false);
 }
}
