package dev.abros.rivet.client;

import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerData;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The catalog ships with Rivet; no remote service or separate catalog screen. */
public final class BundledServers {
 private static Set<String> locked=Set.of();
 public static boolean contains(String address){return locked.contains(key(address));}
 private static String key(String address){String value=address.toLowerCase(Locale.ROOT);return value.endsWith(":25565")?value.substring(0,value.length()-6):value;}
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(BundledServers.class);
 static void sync(Minecraft mc){
  try(var input=BundledServers.class.getResourceAsStream("/rivet-servers.json")){
   if(input==null)throw new java.io.IOException("Missing bundled server catalog");
   var json=Json.parse(new String(input.readNBytes(1024*1024),StandardCharsets.UTF_8));Json.keys(json,"servers");
   var catalog=new ArrayList<ServerCatalog.Entry>();for(var value:json.getAsJsonArray("servers")){var e=value.getAsJsonObject();Json.keys(e,"id","name","address");catalog.add(new ServerCatalog.Entry(Json.str(e,"id"),Json.str(e,"name"),Json.str(e,"address")));}
   locked=catalog.stream().map(e->key(e.address())).collect(java.util.stream.Collectors.toUnmodifiableSet());
   Path stateFile=mc.gameDirectory.toPath().resolve("rivet/server-catalog.json");var old=new ArrayList<ServerCatalog.Tracked>();
   if(Files.exists(stateFile)){var saved=Json.read(stateFile);Json.keys(saved,"servers");for(var value:saved.getAsJsonArray("servers")){var e=value.getAsJsonObject();Json.keys(e,"id","name","address","managed");old.add(new ServerCatalog.Tracked(new ServerCatalog.Entry(Json.str(e,"id"),Json.str(e,"name"),Json.str(e,"address")),e.get("managed").getAsBoolean()));}}
   // ServerList.load logs errors internally; validate before it can appear empty.
   var listFile=mc.gameDirectory.toPath().resolve("servers.dat");
   if(Files.exists(listFile)){
    var saved=net.minecraft.nbt.NbtIo.read(listFile);
    if(saved==null||!saved.contains("servers",9))throw new java.io.IOException("Invalid Minecraft server list; kept unchanged");
    var values=saved.getList("servers",10);
    if(saved.getList("servers",10).size()!=((net.minecraft.nbt.ListTag)saved.get("servers")).size())throw new java.io.IOException("Invalid Minecraft server entries; kept unchanged");
    for(var value:values){var entry=(net.minecraft.nbt.CompoundTag)value;if(!entry.contains("name",8)||!entry.contains("ip",8))throw new java.io.IOException("Invalid Minecraft server entry; kept unchanged");ServerData.read(entry);}
   }
   var servers=new ServerList(mc);servers.load();var rows=new ArrayList<ServerCatalog.Row>();for(int i=0;i<servers.size();i++)rows.add(new ServerCatalog.Row(servers.get(i).name,servers.get(i).ip));
   var plan=ServerCatalog.reconcile(rows,old,catalog);
   for(var change:plan.changes())if(change.index()>=0&&change.entry()!=null){var data=servers.get(change.index());data.name=change.entry().name();if(!data.ip.equals(change.entry().address()))data.setIconBytes(null);data.ip=change.entry().address();}
   plan.changes().stream().filter(c->c.index()>=0&&c.entry()==null).sorted(Comparator.comparingInt(ServerCatalog.Change::index).reversed()).forEach(c->servers.remove(servers.get(c.index())));
   for(var change:plan.changes())if(change.index()<0)servers.add(new ServerData(change.entry().name(),change.entry().address(),ServerData.Type.OTHER),false);
   if(!plan.changes().isEmpty()){
    servers.save();
    var persisted=new ServerList(mc);persisted.load();
    if(persisted.size()!=servers.size())throw new java.io.IOException("Catalog was not saved; tracking kept unchanged");
    for(int i=0;i<servers.size();i++)if(!persisted.get(i).name.equals(servers.get(i).name)||!persisted.get(i).ip.equals(servers.get(i).ip))throw new java.io.IOException("Catalog was not saved; tracking kept unchanged");
   }
   if(!plan.state().equals(old)){var entries=new ArrayList<Map<String,Object>>();for(var t:plan.state())entries.add(Map.of("id",t.entry().id(),"name",t.entry().name(),"address",t.entry().address(),"managed",t.managed()));Json.write(stateFile,Map.of("servers",entries));}
  }catch(Exception error){LOG.warn("Could not synchronize bundled server catalog",error);}
 }
}
