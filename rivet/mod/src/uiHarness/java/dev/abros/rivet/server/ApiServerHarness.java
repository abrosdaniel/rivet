package dev.abros.rivet.server;

import dev.abros.rivet.api.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;

/** Exercises public discovery in the real NeoForge classloader without a client. */
@EventBusSubscriber(modid="rivet")
public final class ApiServerHarness {
    private static RivetApi retained;
    private static net.minecraft.server.MinecraftServer nativeServer;
    private static java.util.concurrent.ExecutorService checks;
    private static java.util.concurrent.Future<java.util.UUID> keyCheck;
    private static int phase;
    private static String httpSecret,httpEtag;
    private static long deadline;
    private static dev.abros.rivet.core.api.ApiKeys inspector;
    private static dev.abros.rivet.core.api.ApiKeys.Grant grant;
    private static boolean enabled() { return "true".equals(System.getenv("RIVET_API_SMOKE")); }
    @SubscribeEvent public static void started(ServerStartedEvent event) throws Exception {
        if (!enabled()) return;
        retained = RivetApi.connect("rivet").orElseThrow();
        var snapshot = retained.serverStatus();
        if (snapshot.status().onlinePlayers() != 0 || !snapshot.status().capabilities().contains("server.status"))
            throw new IllegalStateException("Invalid native status snapshot");
        if (!retained.serverStatusEvents(snapshot.cursor(), 1).changes().isEmpty())
            throw new IllegalStateException("Unchanged status emitted an event");
        try { RivetApi.connect("missing_consumer"); throw new IllegalStateException("Uninstalled consumer accepted"); }
        catch (ApiException expected) { if (expected.code() != ApiException.Code.INVALID_ARGUMENT) throw expected; }
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            if (!executor.submit(retained::serverStatus).get().equals(snapshot))
                throw new IllegalStateException("Worker sees different snapshot");
        }
        var domains=retained.publicDomains();
        if(!domains.capabilities().get(5,java.util.concurrent.TimeUnit.SECONDS).contains("map.read")||domains.publications(false,"",java.util.OptionalLong.empty()).get(5,java.util.concurrent.TimeUnit.SECONDS)==null)
            throw new IllegalStateException("Native Java domain API failed");
        try(var watch=domains.watch(new DomainWatch.Publications(false))){
            long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);DomainSubscription.Event<DomainData.Publications> update=null;
            while(update==null&&System.nanoTime()<until){update=watch.poll().orElse(null);if(update==null)Thread.sleep(10);}
            if(!(update instanceof DomainSubscription.Snapshot<DomainData.Publications>))throw new IllegalStateException("Native Java subscription failed");
        }
        System.out.println("RIVET_JAVA_DOMAINS_NATIVE_OK subscriptions=true");
        System.out.println("RIVET_API_SMOKE_STARTED");
        var server=event.getServer();nativeServer=server;
        if(ServerDatabase.settings().flag("api.terrain.enabled")){server.overworld().getChunk(0,0);server.overworld().setBlock(new net.minecraft.core.BlockPos(0,300,0),net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(),3);}
        else{try{retained.terrain();throw new IllegalStateException("Disabled terrain exposed");}catch(ApiException expected){if(expected.code()!=ApiException.Code.MODULE_DISABLED)throw expected;}}
        var console=server.createCommandSourceStack();
        if(!ServerApiKeys.console(console))throw new IllegalStateException("Console rejected");
        var player=new net.minecraft.server.level.ServerPlayer(server,server.overworld(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ApiAdminTest"),net.minecraft.server.level.ClientInformation.createDefault());
        var playerStack=console.withEntity(player).withPermission(4);
        var blockStack=console.withSource(net.minecraft.commands.CommandSource.NULL).withPermission(4);
        var rconStack=new net.minecraft.server.rcon.RconConsoleSource(server).createCommandSourceStack();
        for(var denied:java.util.List.of(playerStack,blockStack,rconStack)) {
            if(ServerApiKeys.console(denied))throw new IllegalStateException("Non-console source accepted");
            var node=server.getCommands().getDispatcher().findNode(java.util.List.of("rivet","api"));
            if(node==null||node.canUse(denied))throw new IllegalStateException("Non-console command visible");
        }
        checks=java.util.concurrent.Executors.newSingleThreadExecutor();
        inspector=new dev.abros.rivet.core.api.ApiKeys(ServerDatabase.get(),dev.abros.rivet.core.api.ApiKeys.Limits.from(ServerDatabase.settings()),java.time.Clock.systemUTC());
        String application="native_"+java.util.UUID.randomUUID().toString().replace("-", "");
        server.getCommands().performPrefixedCommand(console,"rivet api keys create "+application+" \"server.status.read,sessions.link,events.read,map.read,map.tiles.read,map.tiles.profile.staff,map.tiles.profile.caves,map.render.manage,tasks.read,chat.read,tasks.write,websocket.connect,websocket.ticket\" 1 60");
        deadline=System.currentTimeMillis()+45000;
        keyCheck=checks.submit(()->{
            while(System.currentTimeMillis()<deadline){
                var rows=inspector.list().stream().filter(key->key.application().equals(application)).toList();
                if(!rows.isEmpty()){
                    var key=rows.getFirst();
                    var path=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("rivet/api-keys/"+key.id()+".secret");
                    String secret=java.nio.file.Files.readString(path).trim();
                    grant=inspector.authorize(secret,"server.status.read");
                    checkExtendedDomains();
                    if(ServerDatabase.settings().flag("api.http.enabled")){checkHttp(secret,false);checkTerrain(secret);checkWebSocket(secret);checkBrowserTicket(secret);}
                    else try(var socket=new java.net.Socket()){socket.connect(new java.net.InetSocketAddress("127.0.0.1",ServerDatabase.settings().number("api.http.port")),200);throw new IllegalStateException("Disabled REST port is open");}catch(java.net.ConnectException expected){}
                    return key.id();
                }
                Thread.sleep(25);
            }
            throw new IllegalStateException("Console key was not issued");
        });
    }
    private static void checkExtendedDomains()throws Exception{
        var id=java.util.UUID.randomUUID();ServerFeatures.communityStore().seen(new dev.abros.rivet.core.CommunityStore.Actor(id.toString(),"ApiNative",false,false));var api=retained.domains(id);
        for(var result:java.util.List.of(api.onlinePlayers(""),api.positions("",""),api.waystones("",""))){try{result.get(5,java.util.concurrent.TimeUnit.SECONDS);throw new IllegalStateException("Offline map access accepted");}catch(java.util.concurrent.ExecutionException denied){if(!(denied.getCause() instanceof ApiException e)||e.code()!=ApiException.Code.FORBIDDEN)throw denied;}}
        System.out.println("RIVET_LIVE_MAP_OFFLINE_DENIED_OK");
        checkLivePositions();
        try(var owner=retained.mapLayers()){
            var layer=owner.register(new MapLayersApi.LayerSpec("native_layers","Native layers",MapLayersApi.Audience.publicAccess()));
            var object=new MapLayersApi.ObjectSpec("private",new DomainData.Marker("minecraft:overworld","Native",0,64,0,1,"flag"),MapLayersApi.Audience.restricted(java.util.Set.of(id)));
            long objectRevision=layer.put(object,0);
            if(!retained.publicDomains().mapLayerObjects(layer.id(),"",java.util.OptionalLong.empty()).get().items().isEmpty())throw new IllegalStateException("Private layer object leaked");
            if(api.mapLayerObjects(layer.id(),"",java.util.OptionalLong.empty()).get().items().size()!=1)throw new IllegalStateException("Layer object unavailable to owner audience");
            layer.remove("private",objectRevision);layer.close();
            var replacement=owner.register(new MapLayersApi.LayerSpec("native_layers","Replacement",MapLayersApi.Audience.publicAccess()));
            layer.close();replacement.put(object,0);
        }
        if(!retained.publicDomains().mapLayers("",java.util.OptionalLong.empty()).get().items().isEmpty())throw new IllegalStateException("Owner close left layers behind");
        System.out.println("RIVET_MAP_LAYERS_NATIVE_OK private=true revision=true replacement=true cleanup=true");
        var receipt=api.execute(java.util.UUID.randomUUID(),System.currentTimeMillis(),new DomainCommand.SubmitReport("Native support","technical")).get(3,java.util.concurrent.TimeUnit.SECONDS);
        if(!api.report(java.util.UUID.fromString(receipt.id())).get().message().equals("Native support"))throw new IllegalStateException("Report API mismatch");
        if(api.skin(id).get().uuid().equals(id)==false||api.statistics(id).get().uuid().equals(id)==false)throw new IllegalStateException("Player metadata mismatch");
        try{api.staffReports("all","").get(5,java.util.concurrent.TimeUnit.SECONDS);throw new IllegalStateException("Offline nonstaff elevated");}catch(java.util.concurrent.ExecutionException denied){if(!(denied.getCause() instanceof ApiException e)||e.code()!=ApiException.Code.FORBIDDEN)throw denied;}
        if(net.neoforged.fml.ModList.get().isLoaded("luckperms")){
            nativeServer.submit(()->nativeServer.getCommands().performPrefixedCommand(nativeServer.createCommandSourceStack(),"lp user "+id+" permission set rivet.reports true")).get(3,java.util.concurrent.TimeUnit.SECONDS);
            long until=System.currentTimeMillis()+5000;while(!ServerDomainAccess.allowed(id,"rivet.reports")&&System.currentTimeMillis()<until)Thread.sleep(50);
            api.staffReports("all","").get(5,java.util.concurrent.TimeUnit.SECONDS);
            nativeServer.submit(()->nativeServer.getCommands().performPrefixedCommand(nativeServer.createCommandSourceStack(),"lp user "+id+" permission unset rivet.reports")).get(3,java.util.concurrent.TimeUnit.SECONDS);
            until=System.currentTimeMillis()+5000;while(ServerDomainAccess.allowed(id,"rivet.reports")&&System.currentTimeMillis()<until)Thread.sleep(50);
            try{api.staffReports("all","").get(5,java.util.concurrent.TimeUnit.SECONDS);throw new IllegalStateException("Revoked offline staff permission accepted");}catch(java.util.concurrent.ExecutionException denied){if(!(denied.getCause() instanceof ApiException e)||e.code()!=ApiException.Code.FORBIDDEN)throw denied;}
            for(String right:java.util.List.of("rivet.restart","rivet.maintenance","rivet.announce","rivet.events")){
                nativeServer.submit(()->nativeServer.getCommands().performPrefixedCommand(nativeServer.createCommandSourceStack(),"lp user "+id+" permission set "+right+" true")).get(3,java.util.concurrent.TimeUnit.SECONDS);
                long ready=System.currentTimeMillis()+5000;while(!ServerDomainAccess.allowed(id,right)&&System.currentTimeMillis()<ready)Thread.sleep(50);
            }
            var control=api.serverControl().get(3,java.util.concurrent.TimeUnit.SECONDS);
            var maintained=api.execute(java.util.UUID.randomUUID(),System.currentTimeMillis(),new DomainCommand.SetMaintenance(control.revision(),true,5,"Native maintenance")).get(3,java.util.concurrent.TimeUnit.SECONDS);
            awaitControl(()->ServerManagement.maintenance);
            var scheduled=api.execute(java.util.UUID.randomUUID(),System.currentTimeMillis(),new DomainCommand.ScheduleRestart(maintained.revision(),120,"Native restart")).get(3,java.util.concurrent.TimeUnit.SECONDS);
            awaitControl(()->ServerManagement.restartAt>System.currentTimeMillis());
            var cancelled=api.execute(java.util.UUID.randomUUID(),System.currentTimeMillis(),new DomainCommand.ScheduleRestart(scheduled.revision(),0,"")).get(3,java.util.concurrent.TimeUnit.SECONDS);
            awaitControl(()->ServerManagement.restartAt==0);
            api.execute(java.util.UUID.randomUUID(),System.currentTimeMillis(),new DomainCommand.SetMaintenance(cancelled.revision(),false,0,"")).get(3,java.util.concurrent.TimeUnit.SECONDS);
            awaitControl(()->!ServerManagement.maintenance);
            var group=api.execute(java.util.UUID.randomUUID(),System.currentTimeMillis(),new DomainCommand.CreateGroup("API group","Native private markers","Город")).get();
            var groupId=java.util.UUID.fromString(group.id());
            var members=api.groupPeople(groupId,DomainData.GroupPeople.MEMBERS,"").get();
            if(members.items().size()!=1||!members.items().getFirst().id().equals(id))throw new IllegalStateException("Group members mismatch");
            var marker=api.execute(java.util.UUID.randomUUID(),System.currentTimeMillis(),new DomainCommand.SaveGroupMarker(groupId,members.revision(),java.util.Optional.empty(),0,"Base","Native marker",new DomainData.Location("Base","minecraft:overworld",0,64,0,true))).get();
            var markers=api.groupMarkers(groupId,"").get();
            if(markers.items().size()!=1||!markers.items().getFirst().location().membersOnly())throw new IllegalStateException("Private marker mismatch");
            api.execute(java.util.UUID.randomUUID(),System.currentTimeMillis(),new DomainCommand.DeleteGroupMarker(groupId,markers.revision(),java.util.UUID.fromString(marker.id()),Long.parseLong(marker.revision()))).get();
            var event=api.execute(java.util.UUID.randomUUID(),System.currentTimeMillis(),new DomainCommand.CreateScheduledEvent("Native private series","Details",java.time.Instant.now().plusSeconds(3600).toString(),10,"invited",java.util.Optional.empty(),java.util.List.of(),60,"weekly",2,"Europe/Warsaw")).get();
            if(api.eventSchedule(java.util.UUID.fromString(event.id())).get().occurrences().size()!=2)throw new IllegalStateException("Event series mismatch");
            var announcement=api.execute(java.util.UUID.randomUUID(),System.currentTimeMillis(),new DomainCommand.ScheduleAnnouncement("Native scheduled",java.time.Instant.now().plusSeconds(600).toString())).get();
            if(api.scheduledAnnouncements().get().items().size()!=1)throw new IllegalStateException("Announcement schedule mismatch");
            api.execute(java.util.UUID.randomUUID(),System.currentTimeMillis(),new DomainCommand.CancelAnnouncement(java.util.UUID.fromString(announcement.id()),Long.parseLong(announcement.revision()))).get();
            System.out.println("RIVET_COMMUNITY_DETAILS_NATIVE_OK markers=true members=true series=true announcements=true");
            System.out.println("RIVET_SERVER_CONTROL_NATIVE_OK offline=true maintenance=true restart=true cancellation=true");
            System.out.println("RIVET_OFFLINE_STAFF_NATIVE_OK luckperms=true granted=true revoked=true");
        }
        api.execute(java.util.UUID.randomUUID(),System.currentTimeMillis(),new DomainCommand.SendChat("global","Native API message")).get(3,java.util.concurrent.TimeUnit.SECONDS);
        if(api.chat("").get().items().stream().noneMatch(m->m.text().contains("Native API message")))throw new IllegalStateException("API chat history missing");
        System.out.println("RIVET_EXTENDED_DOMAINS_NATIVE_OK reports=true metadata=true offlineDenied=true chat=true");
    }
    @SuppressWarnings("unchecked")
    private static void checkLivePositions()throws Exception{
        // Only inside the isolated harness: no tick sees these temporary players.
        nativeServer.submit(()->{
            var list=nativeServer.getPlayerList();
            try{
                var playersField=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");playersField.setAccessible(true);
                var idsField=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");idsField.setAccessible(true);
                var players=(java.util.List<net.minecraft.server.level.ServerPlayer>)playersField.get(list);
                var ids=(java.util.Map<java.util.UUID,net.minecraft.server.level.ServerPlayer>)idsField.get(list);
                var viewer=new net.minecraft.server.level.ServerPlayer(nativeServer,nativeServer.overworld(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ApiViewer"),net.minecraft.server.level.ClientInformation.createDefault());
                var peer=new net.minecraft.server.level.ServerPlayer(nativeServer,nativeServer.overworld(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ApiPeer"),net.minecraft.server.level.ClientInformation.createDefault());
                players.add(viewer);players.add(peer);ids.put(viewer.getUUID(),viewer);ids.put(peer.getUUID(),peer);
                try{
                    peer.setPos(10,64,20);viewer.setPos(0,64,0);
                    var visible=liveSnapshot(viewer.getUUID(),"map/positions",java.util.Map.of());
                    if(visible.getAsJsonArray("items").size()!=1)throw new IllegalStateException("Visible peer missing");
                    var presence=liveSnapshot(viewer.getUUID(),"players/online",java.util.Map.of());
                    if(presence.getAsJsonArray("items").get(0).getAsJsonObject().has("x"))throw new IllegalStateException("Presence leaked coordinates");
                    var store=ServerFeatures.communityStore();
                    store.record("map-position",peer.getUUID().toString(),new dev.abros.rivet.core.map.MapPositionPolicy("hidden","all").json());
                    if(!liveSnapshot(viewer.getUUID(),"map/positions",java.util.Map.of()).getAsJsonArray("items").isEmpty())throw new IllegalStateException("Hidden peer leaked");
                    store.record("map-position",peer.getUUID().toString(),new dev.abros.rivet.core.map.MapPositionPolicy("nearby","all").json());
                    peer.setPos(100000,64,0);
                    if(!liveSnapshot(viewer.getUUID(),"map/positions",java.util.Map.of()).getAsJsonArray("items").isEmpty())throw new IllegalStateException("Distant peer leaked");
                    store.deleteRecord("map-position",peer.getUUID().toString());
                    if(!liveSnapshot(viewer.getUUID(),"map/positions",java.util.Map.of("dimension","minecraft:the_nether")).getAsJsonArray("items").isEmpty())throw new IllegalStateException("Dimension filter ignored");
                }finally{players.remove(viewer);players.remove(peer);ids.remove(viewer.getUUID());ids.remove(peer.getUUID());}
                System.out.println("RIVET_LIVE_MAP_NATIVE_OK visible=true hidden=true nearby=true presence=true dimension=true");
            }catch(Exception failure){throw new RuntimeException(failure);}
        }).get(5,java.util.concurrent.TimeUnit.SECONDS);
    }
    private static com.google.gson.JsonObject liveSnapshot(java.util.UUID viewer,String resource,java.util.Map<String,String> query)throws Exception{
        var prepared=ServerLiveMap.prepare(nativeServer,viewer,"");
        var policies=ServerFeatures.communityStore().apiPositionPolicies(new dev.abros.rivet.core.CommunityStore.Actor(viewer.toString(),"",false,false),prepared.input());
        return ServerLiveMap.finish(nativeServer,viewer,prepared,policies,resource,"",query.getOrDefault("dimension",""));
    }
    private static void awaitControl(java.util.function.BooleanSupplier check)throws Exception{long until=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<until){if(nativeServer.submit(check::getAsBoolean).get(2,java.util.concurrent.TimeUnit.SECONDS))return;Thread.sleep(50);}throw new IllegalStateException("Server control not applied");}
    private static void checkTerrain(String secret)throws Exception{
        nativeServer.submit(()->{var level=nativeServer.overworld();for(int x=0;x<3;x++){level.setBlock(new net.minecraft.core.BlockPos(x,20,0),net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState(),3);for(int y=21;y<25;y++)level.setBlock(new net.minecraft.core.BlockPos(x,y,0),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);level.setBlock(new net.minecraft.core.BlockPos(x,25,0),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);}}).get(5,java.util.concurrent.TimeUnit.SECONDS);
        int waterTint=nativeServer.submit(()->{var level=nativeServer.overworld();level.setBlock(new net.minecraft.core.BlockPos(4,298,4),net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(),2);for(int y=299;y<=300;y++)level.setBlock(new net.minecraft.core.BlockPos(4,y,4),net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(),2);return level.getBiome(new net.minecraft.core.BlockPos(4,300,4)).value().getWaterColor();}).get(5,java.util.concurrent.TimeUnit.SECONDS);
        String base="http://127.0.0.1:"+ServerDatabase.settings().number("api.http.port")+"/api/v1/map/terrain";
        try(var client=java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build()){
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/jobs")).header("Authorization","Bearer "+secret).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"dimension\":\"minecraft:overworld\",\"minChunkX\":0,\"minChunkZ\":0,\"maxChunkX\":0,\"maxChunkZ\":0}")).build();
            var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException("Terrain job refused "+response.statusCode());
            var id=java.util.UUID.fromString(com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString());
            TerrainApi.Job job;do{Thread.sleep(50);job=retained.terrain().job(id).get(3,java.util.concurrent.TimeUnit.SECONDS);}while(!java.util.Set.of("COMPLETE","FAILED").contains(job.state())&&System.currentTimeMillis()<deadline);
            if(!job.state().equals("COMPLETE")||job.rendered()!=1||job.skipped()!=0)throw new IllegalStateException("Native terrain did not render loaded chunk");
            var tile=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/tile.png?dimension=minecraft:overworld&zoom=0&x=0&z=0")).header("Authorization","Bearer "+secret).GET().build();
            var png=client.send(tile,java.net.http.HttpResponse.BodyHandlers.ofByteArray());if(png.statusCode()!=200||!png.headers().firstValue("content-type").orElse("").equals("image/png"))throw new IllegalStateException("Native PNG route failed");
            var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png.body()));int color=image.getRGB(0,0);if((color>>>24)!=255||(color>>>16&255)<=(color&255))throw new IllegalStateException("Top block missing from terrain");
            if(image.getWidth()!=256||!retained.terrain().projection().horizontal().equals("16*x"))throw new IllegalStateException("Detailed surface metadata missing");
            int waterColor=image.getRGB(4*16+8,4*16+8);int expectedRed=(int)Math.round((waterTint>>>16&255)*180/255.0+240*75/255.0);if((waterColor>>>24)!=255||Math.abs((waterColor>>>16&255)-expectedRed)>2)throw new IllegalStateException("Liquid depth/alpha surface incorrect");
            System.out.println("RIVET_GEOMETRY_NATIVE_OK surface256=true liquidColumn=true alpha=true");
            var isoRequest=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/tile.png?profile=isometric&dimension=minecraft:overworld&zoom=0&x=0&z=-10")).header("Authorization","Bearer "+secret).GET().build();
            var isoResponse=client.send(isoRequest,java.net.http.HttpResponse.BodyHandlers.ofByteArray());if(isoResponse.statusCode()!=200||javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(isoResponse.body())).getWidth()!=256)throw new IllegalStateException("Isometric HTTP tile missing");
            var modelImage=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(isoResponse.body()));
            if(modelImage.getRGB(0,154)!=0||modelImage.getRGB(0,157)==0||!retained.terrain().materials().active()||retained.terrain().materials().resolved()==0)throw new IllegalStateException("Local slab model/texture not applied");
            String privateUrl=base+"/tile.png?profile=staff&dimension=minecraft:overworld&zoom=0&x=0&z=0";
            var privateResponse=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(privateUrl)).header("Authorization","Bearer "+secret).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if(privateResponse.statusCode()!=200||!privateResponse.headers().firstValue("Cache-Control").orElse("").equals("private, no-store"))throw new IllegalStateException("Private cache policy missing");
            var privateImage=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(privateResponse.body()));if(privateImage.getRGB(24,8)!=0||privateImage.getRGB(0,0)==0)throw new IllegalStateException("Private mask failed");
            var denied=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(privateUrl.replace("profile=staff","profile=vault"))).header("Authorization","Bearer "+secret).header("If-None-Match","*").GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());if(denied.statusCode()!=403)throw new IllegalStateException("Unauthorized profile/cache was exposed");
            var listing=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/profiles")).header("Authorization","Bearer "+secret).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());if(listing.statusCode()!=200||!listing.body().contains("staff")||listing.body().contains("vault"))throw new IllegalStateException("Profile discovery leaked");
            var caves=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(privateUrl.replace("profile=staff","profile=caves"))).header("Authorization","Bearer "+secret).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if(caves.statusCode()!=200)throw new IllegalStateException("Cave API unavailable");var caveImage=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(caves.body()));int caveColor=caveImage.getRGB(0,0);if(caveImage.getRGB(1,0)!=0||(caveColor>>>24)!=255||(caveColor&255)<=(caveColor>>>16&255))throw new IllegalStateException("Cave floor or mask incorrect");
            if(!retained.terrain().profile("caves").slice().orElseThrow().equals(new TerrainApi.Slice(16,31)))throw new IllegalStateException("Cave slice missing");
            System.out.println("RIVET_CAVE_NATIVE_OK floor=true masked=true slice=true");
            System.out.println("RIVET_PRIVATE_TERRAIN_NATIVE_OK mask=true keyScoped=true conditionalDenied=true discovery=true");
            if(retained.terrain().tile(new TerrainApi.Tile("minecraft:overworld",1,0,0)).get().png().length==0)throw new IllegalStateException("Overview missing");
        }
        var skipped=retained.terrain().render(new TerrainApi.Region("minecraft:overworld",127,0,127,0)).get();
        do{Thread.sleep(50);skipped=retained.terrain().job(skipped.id()).get();}while(!skipped.state().equals("COMPLETE")&&System.currentTimeMillis()<deadline);
        if(skipped.skipped()!=1||skipped.rendered()!=0)throw new IllegalStateException("Unloaded chunk was not skipped");
        var cancellable=retained.terrain().render(new TerrainApi.Region("minecraft:overworld",0,0,127,0)).get();
        if(!retained.terrain().cancel(cancellable.id()).get().state().equals("CANCELLED"))throw new IllegalStateException("Job cancellation failed");
        System.out.println("RIVET_TERRAIN_NATIVE_OK job=true png=true topBlock=true overview=true skipped=true cancelled=true");
    }
    private static void checkWebSocket(String secret)throws Exception{
        var received=new java.util.concurrent.LinkedBlockingQueue<String>();
        try(var client=java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build()){
            var socket=client.newWebSocketBuilder().header("Authorization","Bearer "+secret).buildAsync(java.net.URI.create("ws://127.0.0.1:"+ServerDatabase.settings().number("api.websocket.port")+"/api/v1/ws"),new java.net.http.WebSocket.Listener(){
                private final StringBuilder text=new StringBuilder();
                public void onOpen(java.net.http.WebSocket ws){ws.request(1);}
                public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket ws,CharSequence part,boolean last){text.append(part);if(last){received.add(text.toString());text.setLength(0);}ws.request(1);return null;}
                public void onError(java.net.http.WebSocket ws,Throwable failure){received.offer("{}");}
            }).get(3,java.util.concurrent.TimeUnit.SECONDS);
            try {
            socket.sendText("{\"type\":\"request\",\"id\":\"status\",\"method\":\"GET\",\"path\":\"/api/v1/server/status\",\"session\":null,\"ifMatch\":null,\"body\":null}",true).join();
            var reply=com.google.gson.JsonParser.parseString(received.poll(5,java.util.concurrent.TimeUnit.SECONDS)).getAsJsonObject();
            if(reply.getAsJsonObject("data").get("status").getAsInt()!=200)throw new IllegalStateException("Native WS request failed");
            socket.sendText("{\"type\":\"watch\",\"id\":\"map\",\"path\":\"map/objects\",\"query\":{},\"session\":null,\"cursor\":null}",true).join();
            var pushed=com.google.gson.JsonParser.parseString(received.poll(5,java.util.concurrent.TimeUnit.SECONDS)).getAsJsonObject();
            if(!pushed.get("type").getAsString().equals("resource.snapshot")||!pushed.getAsJsonObject("data").getAsJsonObject("resource").has("items"))throw new IllegalStateException("Native WS subscription failed");
            socket.sendText("{\"type\":\"subscribe\",\"topic\":\"map.tiles\",\"cursor\":null}",true).join();
            var terrain=com.google.gson.JsonParser.parseString(received.poll(5,java.util.concurrent.TimeUnit.SECONDS)).getAsJsonObject();
            if(!terrain.get("type").getAsString().equals("terrain.snapshot"))throw new IllegalStateException("Native terrain subscription failed");
            nativeServer.execute(()->nativeServer.overworld().setBlock(new net.minecraft.core.BlockPos(0,300,0),net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState(),3));
            boolean updated=false;while(System.currentTimeMillis()<deadline){Thread.sleep(100);var pixels=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(retained.terrain().tile(new TerrainApi.Tile("minecraft:overworld",0,0,0)).get().png()));int c=pixels.getRGB(0,0);if((c&255)>(c>>>16&255)){updated=true;break;}}if(!updated)throw new IllegalStateException("Automatic block change did not update terrain");
            var changed=com.google.gson.JsonParser.parseString(received.poll(5,java.util.concurrent.TimeUnit.SECONDS)).getAsJsonObject();
            if(!changed.get("type").getAsString().equals("terrain.changed")||changed.getAsJsonObject("data").getAsJsonArray("items").isEmpty())throw new IllegalStateException("Terrain update not pushed");
            socket.sendText("{\"type\":\"subscribe\",\"topic\":\"map.tiles.isometric\",\"cursor\":null}",true).join();
            var iso=com.google.gson.JsonParser.parseString(received.poll(5,java.util.concurrent.TimeUnit.SECONDS)).getAsJsonObject();
            if(!iso.getAsJsonObject("data").getAsJsonObject("projection").get("profile").getAsString().equals("isometric"))throw new IllegalStateException("Isometric WS profile missing");
            socket.sendText("{\"type\":\"subscribe\",\"topic\":\"map.tiles\",\"profile\":\"staff\",\"cursor\":null}",true).join();
            var privateSnapshot=com.google.gson.JsonParser.parseString(received.poll(5,java.util.concurrent.TimeUnit.SECONDS)).getAsJsonObject();if(!privateSnapshot.getAsJsonObject("data").get("profile").getAsString().equals("staff"))throw new IllegalStateException("Private WS selection failed");
            socket.sendText("{\"type\":\"subscribe\",\"topic\":\"map.tiles\",\"profile\":\"caves\",\"cursor\":null}",true).join();
            var caveSnapshot=com.google.gson.JsonParser.parseString(received.poll(5,java.util.concurrent.TimeUnit.SECONDS)).getAsJsonObject();if(caveSnapshot.getAsJsonObject("data").getAsJsonObject("slice").get("minY").getAsInt()!=16)throw new IllegalStateException("Cave WS slice missing");
            socket.sendText("{\"type\":\"subscribe\",\"topic\":\"map.tiles\",\"profile\":\"vault\",\"cursor\":null}",true).join();
            var forbidden=com.google.gson.JsonParser.parseString(received.poll(5,java.util.concurrent.TimeUnit.SECONDS)).getAsJsonObject();if(!forbidden.get("type").getAsString().equals("error")||!forbidden.getAsJsonObject("data").get("code").getAsString().equals("FORBIDDEN"))throw new IllegalStateException("Private WS access leaked");socket.abort();
            } finally { socket.abort(); }
        }
        System.out.println("RIVET_WEBSOCKET_NATIVE_OK authenticated=true request=true subscription=true");
    }
    private static void checkBrowserTicket(String secret)throws Exception{
        String base="http://127.0.0.1:"+ServerDatabase.settings().number("api.http.port")+"/api/v1";
        var f=ServerRest.class.getDeclaredField("sessions");f.setAccessible(true);var sessions=(dev.abros.rivet.core.api.PlayerSessions)f.get(null);
        var grant=ServerApiKeys.authorization().authorize(secret,"websocket.ticket");var challenge=sessions.challenge(grant);sessions.confirm(challenge.code(),grant.application(),java.util.UUID.randomUUID().toString(),"TicketFixture");String session=sessions.exchange(grant,challenge.code(),challenge.pollToken()).token();
        try(var client=java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).connectTimeout(java.time.Duration.ofSeconds(2)).build()){
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/websocket/tickets")).header("Authorization","Bearer "+secret).header("X-Rivet-Session",session).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"origin\":\"https://native.example\"}")).build();
            var issued=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());if(issued.statusCode()!=200)throw new IllegalStateException("Ticket issuance failed: "+issued.statusCode());String ticket=com.google.gson.JsonParser.parseString(issued.body()).getAsJsonObject().get("ticket").getAsString();
            var messages=new java.util.concurrent.LinkedBlockingQueue<String>();var closed=new java.util.concurrent.CompletableFuture<Void>();
            var socket=client.newWebSocketBuilder().header("Origin","https://native.example").subprotocols("rivet.v1","rivet.ticket."+ticket).buildAsync(java.net.URI.create("ws://127.0.0.1:"+ServerDatabase.settings().number("api.websocket.port")+"/api/v1/ws"),new java.net.http.WebSocket.Listener(){
                private final StringBuilder text=new StringBuilder();public void onOpen(java.net.http.WebSocket ws){ws.request(1);}
                public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket ws,CharSequence part,boolean last){text.append(part);if(last){messages.add(text.toString());text.setLength(0);}ws.request(1);return null;}
                public java.util.concurrent.CompletionStage<?> onClose(java.net.http.WebSocket ws,int code,String reason){closed.complete(null);return null;}
                public void onError(java.net.http.WebSocket ws,Throwable failure){closed.complete(null);}
            }).get(5,java.util.concurrent.TimeUnit.SECONDS);
            if(!socket.getSubprotocol().equals("rivet.v1"))throw new IllegalStateException("Browser subprotocol missing");
            socket.sendText("{\"type\":\"watch\",\"id\":\"map\",\"path\":\"map/objects\",\"query\":{},\"session\":null,\"cursor\":null}",true).join();String snapshot=messages.poll(5,java.util.concurrent.TimeUnit.SECONDS);if(snapshot==null||!snapshot.contains("resource.snapshot")||snapshot.contains(session)||snapshot.contains(secret))throw new IllegalStateException("Browser subscription failed");
            sessions.revoke(grant,session);closed.get(5,java.util.concurrent.TimeUnit.SECONDS);socket.abort();
        }
        System.out.println("RIVET_BROWSER_TICKET_NATIVE_OK issued=true bound=true subscription=true revoked=true");
    }
    private static void checkHttp(String secret,boolean revoked)throws Exception {
        String base="http://127.0.0.1:"+ServerDatabase.settings().number("api.http.port")+"/api/v1";
        try(var client=java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).connectTimeout(java.time.Duration.ofSeconds(2)).build()){
            if(revoked){
                var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/server/status")).header("Authorization","Bearer "+secret).header("If-None-Match",httpEtag).GET().build();
                if(client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()!=401)throw new IllegalStateException("Revoked REST key accepted with ETag");
                System.out.println("RIVET_REST_NATIVE_OK authentication=true conditional=true revoked=true scope=true query=true");return;
            }
            var mutation=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/tasks")).header("Authorization","Bearer "+secret).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build();
            if(client.send(mutation,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()!=401)throw new IllegalStateException("Native command did not require player session");
            var link=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/sessions/link")).header("Authorization","Bearer "+secret).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build();
            var linked=client.send(link,java.net.http.HttpResponse.BodyHandlers.ofString());
            if(linked.statusCode()!=200||!com.google.gson.JsonParser.parseString(linked.body()).getAsJsonObject().has("pollToken"))throw new IllegalStateException("Native session link POST failed: "+linked.statusCode());
            var domain=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/events")).header("Authorization","Bearer "+secret).GET().build();
            if(client.send(domain,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()!=200)throw new IllegalStateException("Native event read failed");
            for(String route:java.util.List.of("map/objects","map/territories","tasks","chat/history")){
                var resource=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/"+route)).header("Authorization","Bearer "+secret).GET().build();
                int expected=route.startsWith("map/")?200:401;
                if(client.send(resource,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()!=expected)throw new IllegalStateException("Native resource policy failed: "+route);
            }
            var anonymous=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/server/status")).GET().build();
            if(client.send(anonymous,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()!=401)throw new IllegalStateException("Private REST resource became public");
            var authenticated=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/server/status")).header("Authorization","Bearer "+secret).GET().build();
            var response=client.send(authenticated,java.net.http.HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IllegalStateException("REST key refused: "+response.statusCode());
            httpSecret=secret;httpEtag=response.headers().firstValue("etag").orElseThrow();
            var body=com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
            if(body.getAsJsonObject("status").get("onlinePlayers").getAsInt()!=0)throw new IllegalStateException("Wrong HTTP status projection");
            var conditional=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/server/status")).header("Authorization","Bearer "+secret).header("If-None-Match",httpEtag).GET().build();
            if(client.send(conditional,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()!=304)throw new IllegalStateException("REST conditional GET failed");
            String stream=body.getAsJsonObject("cursor").get("stream").getAsString();
            var denied=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/server/status/events?stream="+stream+"&after=0")).header("Authorization","Bearer "+secret).GET().build();
            if(client.send(denied,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()!=403)throw new IllegalStateException("Missing scope bypassed");
            var query=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/server/status?api_key="+secret)).GET().build();
            if(client.send(query,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()!=400)throw new IllegalStateException("Query credential accepted");
        }
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)throws Exception {
        if(!enabled()||keyCheck==null)return;
        if(System.currentTimeMillis()>deadline)throw new IllegalStateException("API key scenario timed out");
        if(!keyCheck.isDone())return;
        var id=keyCheck.get();
        if(phase++==0){
            event.getServer().getCommands().performPrefixedCommand(event.getServer().createCommandSourceStack(),"rivet api keys revoke "+id);
            keyCheck=checks.submit(()->{
                while(System.currentTimeMillis()<deadline){
                    try{inspector.revalidate(grant,"server.status.read");}
                    catch(ApiException failure){if(failure.code()!=ApiException.Code.UNAUTHENTICATED)throw failure;
                        var path=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("rivet/api-keys/"+id+".secret");
                        if(!java.nio.file.Files.exists(path)){if(ServerDatabase.settings().flag("api.http.enabled"))checkHttp(httpSecret,true);return id;}
                    }
                    Thread.sleep(25);
                }
                throw new IllegalStateException("Revoked grant remained valid");
            });
        }else{keyCheck=null;inspector.close();checks.shutdown();System.out.println("RIVET_API_KEYS_NATIVE_OK console=true playerDenied=true blockDenied=true rconDenied=true revoke=true");event.getServer().halt(false);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void stopped(ServerStoppedEvent event) {
        if (!enabled()) return;
        if(checks!=null)checks.shutdownNow();
        if (retained == null) throw new IllegalStateException("API startup scenario did not run");
        if (RivetApi.connect("rivet").isPresent()) throw new IllegalStateException("Stopped API discoverable");
        try { retained.serverStatus(); throw new IllegalStateException("Stopped API reference remains valid"); }
        catch (ApiException expected) { if (expected.code() != ApiException.Code.UNAVAILABLE) throw expected; }
        System.out.println("RIVET_API_SMOKE_OK discovery=true worker=true stopped=true");
    }
}
