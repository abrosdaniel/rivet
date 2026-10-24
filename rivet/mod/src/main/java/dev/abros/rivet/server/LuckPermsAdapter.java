package dev.abros.rivet.server;
import com.google.gson.JsonObject;
import java.util.UUID;
/** Optional permission adapter. Never grants permissions when the API is absent. */
public final class LuckPermsAdapter implements dev.abros.rivet.server.compat.CompatibilityAdapter {
    private static final dev.abros.rivet.core.OptionalIntegration LIFECYCLE=dev.abros.rivet.compat.IntegrationSupport.capability("luckperms","permissions-metadata",LuckPermsAdapter::probe);
    private static String probe()throws ReflectiveOperationException{Class<?> api=Class.forName("net.luckperms.api.LuckPerms"),query=Class.forName("net.luckperms.api.query.QueryOptions"),cached=Class.forName("net.luckperms.api.cacheddata.CachedDataManager"),context=Class.forName("net.luckperms.api.context.ContextManager");api.getMethod("getContextManager");context.getMethod("getQueryOptions",Object.class);context.getMethod("getQueryOptions",Class.forName("net.luckperms.api.model.user.User"));context.getMethod("getStaticQueryOptions");cached.getMethod("getMetaData",query);cached.getMethod("getPermissionData",query);Class.forName("net.luckperms.api.cacheddata.CachedPermissionData").getMethod("checkPermission",String.class);Class.forName("net.luckperms.api.cacheddata.CachedMetaData").getMethod("getPrefix");Class.forName("net.luckperms.api.cacheddata.CachedMetaData").getMethod("getSuffix");Class.forName("net.luckperms.api.cacheddata.CachedMetaData").getMethod("getPrimaryGroup");return "LuckPerms API 5 · QueryOptions";}
    public com.google.gson.JsonObject diagnostics(){var row=LIFECYCLE.diagnostics();row.addProperty("id",id());row.add("capabilities",dev.abros.rivet.core.Json.GSON.toJsonTree(java.util.List.of("permissions","prefix","suffix","role-preview")));return row;}
    public static long revision(){return LIFECYCLE.revision();}
    public static JsonObject profile(net.minecraft.server.level.ServerPlayer player){return LIFECYCLE.call(()->loadProfile(player.getUUID(),player),LuckPermsAdapter::denied);}
    /** One contextual cached permission, without loading profile metadata or unrelated rights. */
    public static boolean allowed(net.minecraft.server.level.ServerPlayer player,String permission){return LIFECYCLE.call(()->{
        Object api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
        var apiType=Class.forName("net.luckperms.api.LuckPerms");
        Object manager=apiType.getMethod("getUserManager").invoke(api);
        Object user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",UUID.class).invoke(manager,player.getUUID());if(user==null)return false;
        Object cached=Class.forName("net.luckperms.api.model.user.User").getMethod("getCachedData").invoke(user);
        Object context=apiType.getMethod("getContextManager").invoke(api);
        Object options=Class.forName("net.luckperms.api.context.ContextManager").getMethod("getQueryOptions",Object.class).invoke(context,player);
        Object permissions=Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("getPermissionData",Class.forName("net.luckperms.api.query.QueryOptions")).invoke(cached,options);
        return Class.forName("net.luckperms.api.cacheddata.CachedPermissionData").getMethod("checkPermission",String.class).invoke(permissions,permission).toString().equals("TRUE");
    },()->false);}
    /** Called only by bounded API workers, never on the server tick. Loads offline users explicitly. */
    public static boolean offlineAllowed(UUID id,String right){return LIFECYCLE.call(()->{
        Object api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
        var apiType=Class.forName("net.luckperms.api.LuckPerms");var managerType=Class.forName("net.luckperms.api.model.user.UserManager");Object manager=apiType.getMethod("getUserManager").invoke(api);
        // A command or another integration can already own this offline user's live data.
        // Do not reload it from storage or clean it up while that owner is modifying it.
        Object user=managerType.getMethod("getUser",UUID.class).invoke(manager,id);boolean loaded=user==null;
        if(loaded)user=((java.util.concurrent.CompletableFuture<?>)managerType.getMethod("loadUser",UUID.class).invoke(manager,id)).get(3,java.util.concurrent.TimeUnit.SECONDS);
        try{Object cached=Class.forName("net.luckperms.api.model.user.User").getMethod("getCachedData").invoke(user);Object context=apiType.getMethod("getContextManager").invoke(api);
            Object options=Class.forName("net.luckperms.api.context.ContextManager").getMethod("getStaticQueryOptions").invoke(context);
            Object permissions=Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("getPermissionData",Class.forName("net.luckperms.api.query.QueryOptions")).invoke(cached,options);
            var check=Class.forName("net.luckperms.api.cacheddata.CachedPermissionData").getMethod("checkPermission",String.class);
            return check.invoke(permissions,"rivet.admin").toString().equals("TRUE")||check.invoke(permissions,right).toString().equals("TRUE");
        }finally{if(loaded)managerType.getMethod("cleanupUser",Class.forName("net.luckperms.api.model.user.User")).invoke(manager,user);}
    },()->false);}
    public dev.abros.rivet.core.OptionalIntegration lifecycle(){return LIFECYCLE;}
    public String id(){return "luckperms";}
    public String status(){return LIFECYCLE.status();}
    public void clear(){LIFECYCLE.reset();SAVING.clear();}
    public JsonObject execute(net.minecraft.server.level.ServerPlayer actor,JsonObject request,dev.abros.rivet.api.IntegrationApi.Identity identities){throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.luckperms_use_rivet_permission_and_profile_16a8a657"));}
    private static JsonObject denied(){var result=new JsonObject();var caps=new JsonObject();for(String right:dev.abros.rivet.core.PermissionCatalog.nodes())caps.addProperty(right,false);result.add("capabilities",caps);return result;}
    public static JsonObject profile(UUID id){return LIFECYCLE.call(()->loadProfile(id,null),LuckPermsAdapter::denied);}
    public static com.google.gson.JsonArray roles(){return LIFECYCLE.call(LuckPermsAdapter::loadRoles,com.google.gson.JsonArray::new);}

    private static final java.util.Set<String> SAVING=new java.util.HashSet<>();
    public static boolean mayEdit(net.minecraft.server.level.ServerPlayer p){return AuthServer.authenticated(p)&&ServerFeatures.admin(p)&&(p.hasPermissions(2)||allowed(p,"rivet.permissions.manage"));}
    private static java.util.List<Object> directNodes(Object group,String key)throws ReflectiveOperationException{
        var nodes=(java.util.Collection<?>)Class.forName("net.luckperms.api.model.group.Group").getMethod("getNodes").invoke(group);var type=Class.forName("net.luckperms.api.node.Node");var result=new java.util.ArrayList<Object>();
        for(var node:nodes)if(type.getMethod("getKey").invoke(node).equals(key)&&!(boolean)type.getMethod("hasExpiry").invoke(node)){
            Object contexts=type.getMethod("getContexts").invoke(node);
            if((boolean)Class.forName("net.luckperms.api.context.ContextSet").getMethod("isEmpty").invoke(contexts))result.add(node);
        }return result;
    }
    /** Read a group's global assignments once, rather than traversing all nodes per permission. */
    private static JsonObject assignments(Object group)throws ReflectiveOperationException{
        var result=new JsonObject();for(String key:dev.abros.rivet.core.PermissionCatalog.nodes())result.addProperty(key,"inherit");
        var type=Class.forName("net.luckperms.api.node.Node");var keyMethod=type.getMethod("getKey");var expiry=type.getMethod("hasExpiry");var contexts=type.getMethod("getContexts");var value=type.getMethod("getValue");var empty=Class.forName("net.luckperms.api.context.ContextSet").getMethod("isEmpty");
        for(Object node:(java.util.Collection<?>)Class.forName("net.luckperms.api.model.group.Group").getMethod("getNodes").invoke(group)){
            String key=(String)keyMethod.invoke(node);
            if(result.has(key)&&result.get(key).getAsString().equals("inherit")&&!(boolean)expiry.invoke(node)&&(boolean)empty.invoke(contexts.invoke(node)))result.addProperty(key,String.valueOf(value.invoke(node)));
        }return result;
    }
    private static String direct(Object group,String key)throws ReflectiveOperationException{var nodes=directNodes(group,key);if(nodes.isEmpty())return "inherit";return String.valueOf(Class.forName("net.luckperms.api.node.Node").getMethod("getValue").invoke(nodes.getFirst()));}
    private static void assign(Object group,String key,String value)throws ReflectiveOperationException{
        var type=Class.forName("net.luckperms.api.node.Node");var map=Class.forName("net.luckperms.api.model.group.Group").getMethod("data").invoke(group);var mapType=Class.forName("net.luckperms.api.model.data.NodeMap");
        for(Object node:directNodes(group,key))mapType.getMethod("remove",type).invoke(map,node);
        if(!value.equals("inherit")){Object builder=type.getMethod("builder",String.class).invoke(null,key);var bt=Class.forName("net.luckperms.api.node.NodeBuilder");bt.getMethod("value",boolean.class).invoke(builder,Boolean.parseBoolean(value));mapType.getMethod("add",type).invoke(map,bt.getMethod("build").invoke(builder));}
    }
    public static java.util.concurrent.CompletionStage<JsonObject> editRole(net.minecraft.server.level.ServerPlayer actor,JsonObject input){
        var result=new java.util.concurrent.CompletableFuture<JsonObject>();
        try{
            if(!actor.server.isSameThread()||!mayEdit(actor))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.insufficient_permissions_3f48d3d9"));
            String name=dev.abros.rivet.core.Json.str(input,"role"),key=dev.abros.rivet.core.Json.str(input,"permission"),value=dev.abros.rivet.core.Json.str(input,"value"),expected=dev.abros.rivet.core.Json.str(input,"expected");
            dev.abros.rivet.core.PermissionCatalog.require(key);if(!java.util.Set.of("true","false","inherit").contains(value)||!java.util.Set.of("true","false","inherit").contains(expected)||name.length()>64)throw new IllegalArgumentException("Invalid role edit");
            Object api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);Object manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getGroupManager").invoke(api);var managerType=Class.forName("net.luckperms.api.model.group.GroupManager");var groupType=Class.forName("net.luckperms.api.model.group.Group");Object group=managerType.getMethod("getGroup",String.class).invoke(manager,name);
            if(group==null||SAVING.contains(name)||!direct(group,key).equals(expected))throw new dev.abros.rivet.core.CommunityFailure(dev.abros.rivet.core.CommunityFailure.Code.CONFLICT,dev.abros.rivet.core.Messages.text("rivet.permissions.changed"));
            SAVING.add(name);
            try{assign(group,key,value);
                var saved=(java.util.concurrent.CompletableFuture<?>)managerType.getMethod("saveGroup",groupType).invoke(manager,group);
                saved.whenComplete((unused,failure)->actor.server.execute(()->{try{
                    if(failure!=null){if(direct(group,key).equals(value))assign(group,key,expected);result.completeExceptionally(new IllegalStateException(dev.abros.rivet.core.Messages.text("rivet.permissions.saveFailed")));}
                    else{var reply=new JsonObject();reply.addProperty("saved",true);result.complete(reply);}
                }catch(Exception error){result.completeExceptionally(error);}finally{SAVING.remove(name);}}));
            }catch(Exception error){try{assign(group,key,expected);}finally{SAVING.remove(name);}throw error;}
        }catch(Exception error){result.completeExceptionally(error);}return result;
    }

    private static JsonObject loadProfile(UUID id,net.minecraft.server.level.ServerPlayer player){
        JsonObject result=new JsonObject();JsonObject capabilities=denied().getAsJsonObject("capabilities");result.add("capabilities",capabilities);
        try{
            Object api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager").invoke(api);
            Object user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",UUID.class).invoke(manager,id);if(user==null)return result;
            Class<?> userType=Class.forName("net.luckperms.api.model.user.User");result.addProperty("primaryGroup","");
            Object cached=userType.getMethod("getCachedData").invoke(user);Class<?> cachedType=Class.forName("net.luckperms.api.cacheddata.CachedDataManager");
            Object context=Class.forName("net.luckperms.api.LuckPerms").getMethod("getContextManager").invoke(api);Class<?> contextType=Class.forName("net.luckperms.api.context.ContextManager"),queryType=Class.forName("net.luckperms.api.query.QueryOptions");Object options;if(player!=null)options=contextType.getMethod("getQueryOptions",Object.class).invoke(context,player);else{var live=(java.util.Optional<?>)contextType.getMethod("getQueryOptions",userType).invoke(context,user);options=live.isPresent()?live.get():contextType.getMethod("getStaticQueryOptions").invoke(context);}Object metadata=cachedType.getMethod("getMetaData",queryType).invoke(cached,options);Class<?> metaType=Class.forName("net.luckperms.api.cacheddata.CachedMetaData");Object contextualGroup=metaType.getMethod("getPrimaryGroup").invoke(metadata);if(contextualGroup!=null)result.addProperty("primaryGroup",contextualGroup.toString());
            for(String key:new String[]{"Prefix","Suffix"}){Object v=metaType.getMethod("get"+key).invoke(metadata);if(v!=null)result.addProperty(key.toLowerCase(),v.toString().substring(0,Math.min(512,v.toString().length())));}
            Object permission=cachedType.getMethod("getPermissionData",queryType).invoke(cached,options);Class<?> permissionType=Class.forName("net.luckperms.api.cacheddata.CachedPermissionData");
            for(String key:dev.abros.rivet.core.PermissionCatalog.nodes()){Object tristate=permissionType.getMethod("checkPermission",String.class).invoke(permission,key);capabilities.addProperty(key,tristate.toString().equals("TRUE"));}
        result.addProperty("available",true);
        }catch(ReflectiveOperationException|LinkageError|RuntimeException failure){throw new IllegalStateException("LuckPerms profile API failed",failure); }
        return result;
    }
    private static com.google.gson.JsonArray loadRoles(){
        var rows=new com.google.gson.JsonArray();
        try{
            Object api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getGroupManager").invoke(api);
            var groups=(java.util.Set<?>)Class.forName("net.luckperms.api.model.group.GroupManager").getMethod("getLoadedGroups").invoke(manager);
            var type=Class.forName("net.luckperms.api.model.group.Group");var cachedType=Class.forName("net.luckperms.api.cacheddata.CachedDataManager");var permissionType=Class.forName("net.luckperms.api.cacheddata.CachedPermissionData");
            Object context=Class.forName("net.luckperms.api.LuckPerms").getMethod("getContextManager").invoke(api);Object options=Class.forName("net.luckperms.api.context.ContextManager").getMethod("getStaticQueryOptions").invoke(context);Class<?> queryType=Class.forName("net.luckperms.api.query.QueryOptions");
            var sorted=new java.util.TreeMap<String,Object>();for(var group:groups)sorted.put(String.valueOf(type.getMethod("getName").invoke(group)),group);
            for(var entry:sorted.entrySet()){if(rows.size()>=40)break;var row=new JsonObject();row.addProperty("name",entry.getKey());var caps=new JsonObject();var cached=type.getMethod("getCachedData").invoke(entry.getValue());var permission=cachedType.getMethod("getPermissionData",queryType).invoke(cached,options);
                for(String key:dev.abros.rivet.core.PermissionCatalog.nodes())caps.addProperty(key,permissionType.getMethod("checkPermission",String.class).invoke(permission,key).toString().equals("TRUE"));if(caps.get("rivet.admin").getAsBoolean())for(var right:dev.abros.rivet.core.PermissionCatalog.ENTRIES)if(right.adminDefault())caps.addProperty(right.node(),true);var assignments=assignments(entry.getValue());row.add("direct",assignments);row.add("capabilities",caps);rows.add(row);
            }
        }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){throw new IllegalStateException("LuckPerms roles API failed",unavailable);}
        return rows;
    }

}
