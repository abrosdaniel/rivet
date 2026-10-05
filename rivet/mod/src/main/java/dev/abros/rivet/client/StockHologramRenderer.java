package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.*;
/** Client-only labels from actor-scoped snapshots. Depth-tested and expired; no world entities. */
final class StockHologramRenderer {
 private static final net.minecraft.client.renderer.MultiBufferSource.BufferSource BUFFERS=net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096));
 private static long expires;private static JsonArray panels=new JsonArray();
 static void receive(JsonObject snapshot){panels=snapshot.has("holograms")?snapshot.getAsJsonArray("holograms").deepCopy():new JsonArray();expires=net.minecraft.Util.getMillis()+15000;}
 static void clear(){panels=new JsonArray();expires=0;}
 static void render(RenderLevelStageEvent event){var mc=Minecraft.getInstance();if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES||mc.level==null||mc.player==null||mc.options.hideGui||!HudSettings.INSTANCE.holograms||net.minecraft.Util.getMillis()>expires)return;var camera=event.getCamera();var location=camera.getPosition();var buffers=BUFFERS;float[] previousColor=com.mojang.blaze3d.systems.RenderSystem.getShaderColor().clone();com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);try {
  for(var e:panels){var row=e.getAsJsonObject();if(!Json.str(row,"dimension").equals(mc.level.dimension().location().toString()))continue;double x=row.get("x").getAsDouble(),y=row.get("y").getAsDouble(),z=row.get("z").getAsDouble();if(location.distanceToSqr(x,y,z)>12*12)continue;var stock=net.minecraft.core.BlockPos.containing(x,y-1.5,z);if(!mc.level.hasChunkAt(stock)||mc.level.getBlockEntity(stock)==null)continue;var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(location,net.minecraft.world.phys.Vec3.atCenterOf(stock),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS&&!hit.getBlockPos().equals(stock)){var state=mc.level.getBlockState(stock);boolean paired=state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock&&state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE&&stock.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)).equals(hit.getBlockPos());if(!paired)continue;}var lines=new ArrayList<String>();lines.add(Json.str(row,"title"));for(var item:row.getAsJsonArray("resources")){var res=item.getAsJsonObject();long missing=res.get("missing").getAsLong();lines.add(TaskResourcesScreen.name(Json.str(res,"item"))+" · "+(missing==0?"Собрано":"Ещё "+missing)+" · "+res.get("have")+" / "+res.get("amount"));}if(row.get("remainingTypes").getAsInt()>0)lines.add("Ещё ресурсов: "+row.get("remainingTypes"));var matrix=new Matrix4f(event.getPoseStack().last().pose()).translate((float)(x-location.x),(float)(y-location.y),(float)(z-location.z)).rotate(camera.rotation()).scale(.025f,-.025f,.025f);int offset=-lines.size()*10;for(String line:lines){line=UiKit.fit(mc.font,line,240);mc.font.drawInBatch(Component.literal(line),-mc.font.width(line)/2f,offset,0xFFE0E9EE,false,matrix,buffers,Font.DisplayMode.NORMAL,0xA020252A,15728880);mc.font.drawInBatch(Component.literal(line),-mc.font.width(line)/2f,offset,0xFFE0E9EE,false,new Matrix4f(matrix).translate(0,0,.03f),buffers,Font.DisplayMode.NORMAL,0,15728880);offset+=10;}}
  buffers.endBatch();
  } finally {com.mojang.blaze3d.systems.RenderSystem.setShaderColor(previousColor[0],previousColor[1],previousColor[2],previousColor[3]);}
 }
 private StockHologramRenderer(){}
}
