package dev.abros.rivet.client;
import dev.abros.rivet.server.TradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Native container controls with fixed geometry throughout the exchange. */
public final class TradeScreen extends AbstractContainerScreen<TradeMenu> {
 private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("rivet","textures/gui/trade.png");
 private Button accept,cancel,closeButton,styleButton;private final Button[] pages=new Button[4];
 private boolean cancelArmed,freeArmed,result;
 private boolean givingFree(){return !menu.frozen&&!result&&!menu.ownEmpty&&menu.otherEmpty;}
 boolean rivetStyle(){return TradeAppearance.rivet();}
 private int ink(int nativeColor){return rivetStyle()?UiKit.text():nativeColor;}
 private int muted(int nativeColor){return rivetStyle()?UiKit.muted():nativeColor;}
 private int ready(){return rivetStyle()?(UiPalette.light()?0xFF286B38:0xFF83C69C):0xFF38864B;}
 private int changed(){return rivetStyle()?UiPalette.color(0xFFE2BE75):0xFFDA8B24;}
 private long shownRevision=-1;
 public TradeScreen(TradeMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=256;imageHeight=menu.frameHeight();}
 private Button button(Component label,int x,int y,int w,int h,Runnable action){return addRenderableWidget(new Button.Builder(label,b->action.run()).bounds(leftPos+x,topPos+y,w,h).build());}
 private void close(){if(minecraft.player!=null)minecraft.player.closeContainer();}
 @Override protected void init(){super.init();
  accept=button(Component.empty(),132,114,116,16,()->{if(cancelArmed){cancelArmed=false;press(1);}else if(result&&menu.fullyCollected())close();else if(givingFree()&&!freeArmed){freeArmed=true;update();}else {boolean confirmed=freeArmed;freeArmed=false;press(result?2:confirmed?3:0);}});
  cancel=button(Client.tr("trade.cancel"),8,114,116,16,()->{if(freeArmed)freeArmed=false;else cancelArmed=!cancelArmed;update();});
  closeButton=button(Component.literal("×"),258,5,18,18,this::close);closeButton.setTooltip(Tooltip.create(Client.tr("trade.close.saved")));
  for(int i=0;i<4;i++){final int n=i;pages[i]=button(Component.literal(i%2==0?"‹":"›"),i<2?(i%2==0?7:101):(i%2==0?139:233),100,14,12,()->press(10+n));}
  styleButton=addRenderableWidget(UiActions.tool(Client.tr("trade.style",rivetStyle()?"Rivet":"Minecraft"),UiIcons.BRUSH,leftPos+258,topPos+27,()->{TradeAppearance.toggle();update();}));styleButton.setWidth(18);styleButton.setHeight(18);
  update();
 }
 @Override protected void containerTick(){super.containerTick();update();}
 private void press(int action){if(action<10)net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.abros.rivet.network.TradeWire.Action(menu.containerId,menu.revision,action));else if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
 void update(){if(accept==null)return;result=!menu.status.equals("draft")&&!menu.status.equals("open");menu.resultLayout(result);
  if(shownRevision!=menu.revision){cancelArmed=false;freeArmed=false;shownRevision=menu.revision;}
  accept.setX(leftPos+(result?74:132));accept.setWidth(result?108:116);closeButton.setX(leftPos+(result?218:258));styleButton.setX(closeButton.getX());styleButton.setTooltip(Tooltip.create(Client.tr("trade.style",rivetStyle()?"Rivet":"Minecraft")));styleButton.setMessage(Client.tr("trade.style",rivetStyle()?"Rivet":"Minecraft"));cancel.visible=!result;cancel.setMessage(Client.tr(cancelArmed||freeArmed?"trade.cancel.back":"trade.cancel"));
  accept.active=cancelArmed||(result?menu.fullyCollected()||menu.canCollect:menu.status.equals("draft")?!menu.ownEmpty:menu.dirty||!menu.otherDirty&&(!menu.accepted&&(!menu.ownEmpty||!menu.otherEmpty)));
  if(menu.frozen){accept.active=false;cancel.active=false;}else cancel.active=true;
  accept.setMessage(Client.tr(menu.frozen?"trade.card.frozen":cancelArmed?"trade.cancel.confirm":freeArmed?"trade.free.confirm":result?(menu.fullyCollected()?"trade.done":"trade.collect.all"):menu.status.equals("draft")?"trade.send":menu.dirty?"trade.publish":menu.accepted?"trade.wait.answer":menu.ownEmpty?"trade.gift.accept":"trade.accept"));
  accept.setTooltip(hint().isEmpty()?null:Tooltip.create(Client.tr(hint())));
  for(int i=0;i<4;i++){int page=i<2?menu.ownPage:menu.otherPage,total=i<2?menu.ownPages:menu.otherPages;pages[i].visible=(i<2||!result)&&!(result&&menu.fullyCollected());pages[i].active=i%2==0?page>0:page+1<total;pages[i].setX(leftPos+(i<2?(result?73:7):139)+(i%2==0?0:94));pages[i].setTooltip(Tooltip.create(Client.tr(i%2==0?"trade.page.previous":"trade.page.next")));}
 }
 static String compactCount(int count){
  if(count<1000)return Integer.toString(count);
  int unit=count>=1_000_000_000?1_000_000_000:count>=1_000_000?1_000_000:1000;
  int whole=count/unit,tenth=(int)(((long)count%unit)*10/unit);
  return Integer.toString(whole)+(whole<10&&tenth>0?"."+tenth:"")+(unit==1000?"k":unit==1_000_000?"M":"B");
 }
 private static String exactCount(int count){return String.format(java.util.Locale.ROOT,"%,d",count).replace(',',' ');}
 @Override protected void renderSlotContents(GuiGraphics g,net.minecraft.world.item.ItemStack item,net.minecraft.world.inventory.Slot slot,String countString){
  if(item.getCount()<100){super.renderSlotContents(g,item,slot,countString);return;}
  // Keep native durability, cooldown and drag previews; only abbreviate the count.
  super.renderSlotContents(g,item,slot,"");String label=compactCount(item.getCount());float scale=Math.min(1F,16F/font.width(label));
  g.pose().pushPose();g.pose().translate(slot.x+17,slot.y+17-font.lineHeight*scale,200);g.pose().scale(scale,scale,1);g.drawString(font,label,-font.width(label),0,0xFFFFFF,true);g.pose().popPose();
 }
 @Override protected java.util.List<Component> getTooltipFromContainerItem(net.minecraft.world.item.ItemStack item){var lines=new java.util.ArrayList<>(super.getTooltipFromContainerItem(item));if(item.getCount()>1)lines.add(Client.tr("trade.quantity",exactCount(item.getCount())));return lines;}
 private void text(GuiGraphics g,Component label,int x,int y,int color){g.drawString(font,label,x,y,color,false);}
 private void centered(GuiGraphics g,Component label,int y,int color){text(g,label,(imageWidth-font.width(label))/2,y,color);}
 private void fit(GuiGraphics g,Component label,int x,int y,int width,int color){text(g,Component.literal(UiKit.fit(font,label.getString(),width)),x,y,color);}
 private void bar(GuiGraphics g,int x,int page,int count){if(count<=1)return;g.fill(leftPos+x,topPos+29,leftPos+x+4,topPos+83,rivetStyle()?UiKit.surface(UiKit.Surface.INPUT):0xFF888888);int h=Math.max(5,54/count),y=topPos+29+(54-h)*page/(count-1);g.fill(leftPos+x,y,leftPos+x+4,y+h,rivetStyle()?UiKit.muted():0xFFEEEEEE);}
 @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
  int upperLeft=result?40:0,upperRight=result?216:256;
  if(rivetStyle()){
   UiKit.material(g,leftPos+upperLeft,topPos,upperRight-upperLeft,138);
   UiKit.material(g,leftPos+40,topPos+130,176,104);
   UiKit.surface(g,leftPos+40,topPos+130,176,12,AccessibilityScreen.background(UiKit.surface()));
  }else{
  g.fill(leftPos+upperLeft,topPos,leftPos+upperRight,topPos+138,0xFFC6C6C6);
  g.fill(leftPos+40,topPos+138,leftPos+216,topPos+234,0xFFC6C6C6);
  g.fill(leftPos+upperLeft,topPos,leftPos+upperRight,topPos+1,0xFFFFFFFF);
  g.fill(leftPos+upperLeft,topPos,leftPos+upperLeft+1,topPos+138,0xFFFFFFFF);
  g.fill(leftPos+upperRight-1,topPos,leftPos+upperRight,topPos+138,0xFF555555);
  if(!result){g.fill(leftPos,topPos+137,leftPos+40,topPos+138,0xFF555555);g.fill(leftPos+216,topPos+137,leftPos+256,topPos+138,0xFF555555);}
  g.fill(leftPos+40,topPos+138,leftPos+41,topPos+233,0xFFFFFFFF);
  g.fill(leftPos+215,topPos+138,leftPos+216,topPos+234,0xFF555555);
  g.fill(leftPos+40,topPos+233,leftPos+216,topPos+234,0xFF555555);
  }
  for(var slot:menu.slots){if(!slot.isActive())continue;if(rivetStyle()){UiKit.rounded(g,leftPos+slot.x-1,topPos+slot.y-1,18,18,2,UiKit.surface(UiKit.Surface.INPUT));UiKit.ring(g,leftPos+slot.x-1,topPos+slot.y-1,18,18,2,UiKit.border());}else g.blit(TEXTURE,leftPos+slot.x-1,topPos+slot.y-1,7,29,18,18,256,256);}
  if(!result){if(menu.accepted&&!menu.dirty)g.renderOutline(leftPos+6,topPos+27,110,56,ready());if(menu.otherAccepted&&!menu.otherDirty)g.renderOutline(leftPos+138,topPos+27,110,56,ready());}
  if(!result)for(var entry:menu.changes.entrySet()){var slot=menu.getSlot(entry.getKey());if(!slot.isActive())continue;
   if(slot.getItem().isEmpty()&&!entry.getValue().before().isEmpty()){g.renderItem(entry.getValue().before().copyWithCount(1),leftPos+slot.x,topPos+slot.y);g.flush();g.pose().pushPose();g.pose().translate(0,0,200);g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xAA666666);g.flush();g.pose().popPose();}
   g.renderOutline(leftPos+slot.x-1,topPos+slot.y-1,18,18,changed());
  }
  bar(g,result?183:117,menu.ownPage,menu.ownPages);if(!result)bar(g,249,menu.otherPage,menu.otherPages);
 }
 private void identity(GuiGraphics g,int x,String name,String uuid,boolean online){if(!uuid.isEmpty())PlayerFaceRenderer.draw(g,SkinClient.skin(java.util.UUID.fromString(uuid)),x,16,8);fit(g,Component.literal(name),x+11,16,96,muted(0x505050));g.fill(x+6,22,x+9,25,online?0xFF38864B:0xFF777777);}
 private String hint(){return result?(menu.fullyCollected()?"":!menu.canCollect?"trade.compact.full":"trade.result.collect"):cancelArmed?"trade.compact.cancel":givingFree()?"trade.free.warning":menu.status.equals("draft")?"trade.compact.add":menu.dirty?"trade.compact.draft":menu.otherDirty?"trade.compact.otherEditing":menu.accepted?"":!accept.active?"trade.summary.empty":"";}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){
  if(result){centered(g,Client.tr(menu.status.equals("completed")?"trade.completed.title":"trade.compact.returned"),5,ink(0x303030));fit(g,Client.tr("trade.with",menu.otherName),48,16,160,muted(0x626262));}
  else {text(g,Client.tr("trade.compact.give"),7,5,ink(0x303030));text(g,Client.tr("trade.compact.receive"),139,5,ink(0x303030));
   identity(g,7,minecraft.player.getGameProfile().getName(),minecraft.player.getStringUUID(),true);identity(g,139,menu.otherName,menu.otherId,menu.otherOnline);text(g,Component.literal("⇄"),124,51,muted(0x505050));
   text(g,Client.tr(menu.dirty?"trade.compact.editing":menu.accepted?"trade.compact.ready":"trade.compact.waiting"),7,87,menu.dirty?(rivetStyle()?changed():0x855C17):menu.accepted?(rivetStyle()?ready():0x286B38):muted(0x505050));
   text(g,Client.tr(menu.otherDirty?"trade.compact.editing":menu.otherAccepted?"trade.compact.ready":"trade.compact.waiting"),139,87,menu.otherDirty?(rivetStyle()?changed():0x855C17):menu.otherAccepted?(rivetStyle()?ready():0x286B38):muted(0x505050));
  }
  if(result&&menu.fullyCollected())centered(g,Client.tr("trade.result.empty"),54,rivetStyle()?ready():0x286B38);
  Component ownPage=Component.literal((menu.ownPage+1)+" / "+menu.ownPages);if(!(result&&menu.fullyCollected()))text(g,ownPage,(result?127:61)-font.width(ownPage)/2,102,muted(0x505050));
  if(!result){Component otherPage=Component.literal((menu.otherPage+1)+" / "+menu.otherPages);text(g,otherPage,193-font.width(otherPage)/2,102,muted(0x505050));}
  text(g,menu.frozen?Client.tr("trade.card.frozen"):givingFree()?Client.tr("trade.free.label"):Component.translatable("container.inventory"),46,140,givingFree()?changed():muted(0x404040));if(result&&!menu.fullyCollected()&&!menu.canCollect)fit(g,Client.tr("trade.compact.full"),48,87,160,rivetStyle()?changed():0x705020);
 }
 @Override protected boolean hasClickedOutside(double x,double y,int left,int top,int button){double localX=x-left,localY=y-top;return localY<0||localY>=imageHeight||localX<(result||localY>=138?40:0)||localX>=(result||localY>=138?216:256);}
 @Override public boolean mouseClicked(double x,double y,int button){
  if(button==0&&y>=topPos+29&&y<topPos+83){int ownX=result?183:117;if(x>=leftPos+ownX&&x<leftPos+ownX+5){jump(true,y);return true;}if(!result&&x>=leftPos+249&&x<leftPos+254){jump(false,y);return true;}}
  return super.mouseClicked(x,y,button);
 }
 private void jump(boolean own,double y){int count=own?menu.ownPages:menu.otherPages,page=own?menu.ownPage:menu.otherPage,target=Math.min(count-1,(int)((y-topPos-29)*count/54));for(int i=0;i<Math.abs(target-page);i++)press((own?10:12)+(target>page?1:0));}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
  if(y>=topPos+29&&y<topPos+83){int left=result?74:8;if(x>=leftPos+left&&x<leftPos+left+110){press(dy>0?10:11);return true;}if(!result&&x>=leftPos+140&&x<leftPos+250){press(dy>0?12:13);return true;}}return super.mouseScrolled(x,y,dx,dy);
 }
 private void changeTooltip(GuiGraphics g,TradeMenu.Change change,int mouseX,int mouseY){
  var before=change.before();var after=change.after();var item=after.isEmpty()?before:after;
  String old=exactCount(before.getCount()),now=exactCount(after.getCount());
  var lines=getTooltipFromItem(minecraft,item.copyWithCount(1));
  int row=52+font.width(old)+font.width(now),w=Math.max(row,lines.stream().mapToInt(font::width).max().orElse(0))+16;
  int h=lines.size()*10+32;
  int x=Math.max(4,Math.min(mouseX+12,width-w-4)),y=Math.max(4,Math.min(mouseY+12,height-h-4));
  g.flush();g.pose().pushPose();g.pose().translate(0,0,700);
  if(rivetStyle()){UiKit.material(g,x,y,w,h);UiKit.ring(g,x,y,w,h,8,changed());}else{g.fill(x,y,x+w,y+h,0xF0100010);g.renderOutline(x,y,w,h,changed());}
  for(int i=0;i<lines.size();i++)text(g,lines.get(i),x+8,y+6+i*10,ink(0xFFFFFF));
  int iconY=y+lines.size()*10+12;
  int first=x+8,second=first+28+font.width(old);
  g.renderItem(item.copyWithCount(1),first,iconY);text(g,Component.literal(old),first+18,iconY+4,ink(0xFFFFFF));
  text(g,Component.literal("→"),second-8,iconY+4,changed());
  g.renderItem(item.copyWithCount(1),second+4,iconY);text(g,Component.literal(now),second+22,iconY+4,ink(0xFFFFFF));
  g.flush();g.pose().popPose();
 }
 @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);
  TradeMenu.Change hovered=null;
  if(!result)for(var entry:menu.changes.entrySet()){var slot=menu.getSlot(entry.getKey());if(x>=leftPos+slot.x-1&&x<leftPos+slot.x+17&&y>=topPos+slot.y-1&&y<topPos+slot.y+17){hovered=entry.getValue();break;}}
  if(hovered==null)renderTooltip(g,x,y);else changeTooltip(g,hovered,x,y);
  if(!result&&y>=topPos+15&&y<topPos+26){if(x>=leftPos+139&&x<leftPos+248)g.renderTooltip(font,Component.literal(menu.otherName+" · ").append(Client.tr(menu.otherOnline?"trade.online":"trade.offline")),x,y);else if(x>=leftPos+7&&x<leftPos+116)g.renderTooltip(font,Component.literal(minecraft.player.getGameProfile().getName()),x,y);}
 }
}
