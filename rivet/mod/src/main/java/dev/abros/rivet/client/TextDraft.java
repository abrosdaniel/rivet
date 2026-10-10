package dev.abros.rivet.client;
import com.google.gson.JsonObject;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
/** Latest text is coalesced per account/form; accepted operations keep durable identity. */
final class TextDraft {
 private static final java.util.LinkedHashMap<String,String> latest=new java.util.LinkedHashMap<>();
 private static final CoalescingTasks<String> writes=new CoalescingTasks<>(Client.IO,128,e->com.mojang.logging.LogUtils.getLogger().warn("Draft write failed",e));
 boolean pendingReadable=true;
 private final DraftStore store;private final String server,account,context,key;
 TextDraft(String context){var mc=Minecraft.getInstance();store=new DraftStore(mc.gameDirectory.toPath().resolve("rivet/drafts"));server=mc.getCurrentServer()==null?"":mc.getCurrentServer().ip;account=Json.opt(ServerMenuClient.state,"uuid","");this.context=context;key=mc.gameDirectory+"\0"+server+"\0"+account+"\0"+context;}
 String load(String fallback){if(latest.containsKey(key))return latest.get(key);try{return Json.opt(store.load(server,account,context),"text",fallback);}catch(Exception ex){return fallback;}}
 private void remember(String text){latest.remove(key);latest.put(key,text);while(latest.size()>128)latest.remove(latest.keySet().iterator().next());}
 void save(String text){remember(text);var data=new JsonObject();data.addProperty("text",text);try{writes.submit(key,()->{try{store.save(server,account,context,data);}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot save support draft",ex);}});}catch(java.util.concurrent.RejectedExecutionException full){Client.failure(full);}}
 void clear(){remember("");try{writes.submit(key,()->{try{store.remove(server,account,context);}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot remove support draft",ex);}});}catch(java.util.concurrent.RejectedExecutionException full){Client.failure(full);}}
 JsonObject pending(){try{var saved=store.pending(server,account,context);if(saved!=null){java.util.UUID.fromString(Json.str(saved,"operationId"));saved.get("issuedAt").getAsLong();if(!Json.str(saved,"action").equals(context.startsWith("report-reply:")?"reply":"report"))throw new IllegalArgumentException("Unexpected report operation");}return saved;}catch(Exception error){pendingReadable=false;com.mojang.logging.LogUtils.getLogger().warn("Cannot restore pending report",error);return null;}}
 void pending(JsonObject command,Runnable ready,java.util.function.Consumer<Exception> failed){var copy=command==null?null:command.deepCopy();Client.IO.submit(()->{try{store.pending(server,account,context,copy);Minecraft.getInstance().execute(ready);}catch(Exception error){Minecraft.getInstance().execute(()->failed.accept(error));}});}
}
