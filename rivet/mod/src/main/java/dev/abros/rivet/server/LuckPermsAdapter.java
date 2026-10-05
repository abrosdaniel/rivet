package dev.abros.rivet.server;
import com.google.gson.JsonObject;
import java.util.UUID;
/** Optional read-only adapter. Never grants permissions when the API is absent. */
public final class LuckPermsAdapter implements dev.abros.rivet.server.compat.CompatibilityAdapter {
    private static final dev.abros.rivet.core.OptionalIntegration LIFECYCLE=dev.abros.rivet.compat.IntegrationSupport.capability("luckperms","permissions-metadata",LuckPermsAdapter::probe);
    private static String probe()throws ReflectiveOperationException{Class<?> api=Class.forName("net.luckperms.api.LuckPerms"),query=Class.forName("net.luckperms.api.query.QueryOptions"),cached=Class.forName("net.luckperms.api.cacheddata.CachedDataManager"),context=Class.forName("net.luckperms.api.context.ContextManager");api.getMethod("getContextManager");context.getMethod("getQueryOptions",Object.class);context.getMethod("getQueryOptions",Class.forName("net.luckperms.api.model.user.User"));context.getMethod("getStaticQueryOptions");cached.getMethod("getMetaData",query);cached.getMethod("getPermissionData",query);Class.forName("net.luckperms.api.cacheddata.CachedPermissionData").getMethod("checkPermission",String.class);Class.forName("net.luckperms.api.cacheddata.CachedMetaData").getMethod("getPrefix");Class.forName("net.luckperms.api.cacheddata.CachedMetaData").getMethod("getSuffix");Class.forName("net.luckperms.api.cacheddata.CachedMetaData").getMethod("getPrimaryGroup");return "LuckPerms API 5 · QueryOptions";}
    public com.google.gson.JsonObject diagnostics(){var row=LIFECYCLE.diagnostics();row.addProperty("id",id());row.add("capabilities",dev.abros.rivet.core.Json.GSON.toJsonTree(java.util.List.of("permissions","prefix","suffix","role-preview")));return row;}
    public static long revision(){return LIFECYCLE.revision();}
    public static JsonObject profile(net.minecraft.server.level.ServerPlayer player){return LIFECYCLE.call(()->loadProfile(player.getUUID(),player),LuckPermsAdapter::denied);}
    public dev.abros.rivet.core.OptionalIntegration lifecycle(){return LIFECYCLE;}
    public String id(){return "luckperms";}
    public String status(){return LIFECYCLE.status();}
    public void clear(){LIFECYCLE.reset();}
    public JsonObject execute(net.minecraft.server.level.ServerPlayer actor,JsonObject request,dev.abros.rivet.server.compat.ServerIdentityDirectory identities){throw new IllegalArgumentException("LuckPerms: используйте операции прав и профиля Rivet");}
    private static JsonObject denied(){var result=new JsonObject();var caps=new JsonObject();for(String right:ServerCommands.RIGHTS)caps.addProperty(right,false);for(String right:new String[]{"rivet.admin","rivet.events","rivet.auth.reset","rivet.vote.protected"})caps.addProperty(right,false);result.add("capabilities",caps);return result;}
    public static JsonObject profile(UUID id){return LIFECYCLE.call(()->loadProfile(id,null),LuckPermsAdapter::denied);}
    public static com.google.gson.JsonArray roles(){return LIFECYCLE.call(LuckPermsAdapter::loadRoles,com.google.gson.JsonArray::new);}

    private static JsonObject loadProfile(UUID id,net.minecraft.server.level.ServerPlayer player){
        JsonObject result=new JsonObject();JsonObject capabilities=new JsonObject();capabilities.addProperty("rivet.admin",false);capabilities.addProperty("rivet.events",false);capabilities.addProperty("rivet.auth.reset",false);capabilities.addProperty("rivet.vote.protected",false);result.add("capabilities",capabilities);
        try{
            Object api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager").invoke(api);
            Object user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",UUID.class).invoke(manager,id);if(user==null)return result;
            Class<?> userType=Class.forName("net.luckperms.api.model.user.User");result.addProperty("primaryGroup","");
            Object cached=userType.getMethod("getCachedData").invoke(user);Class<?> cachedType=Class.forName("net.luckperms.api.cacheddata.CachedDataManager");
            Object context=Class.forName("net.luckperms.api.LuckPerms").getMethod("getContextManager").invoke(api);Class<?> contextType=Class.forName("net.luckperms.api.context.ContextManager"),queryType=Class.forName("net.luckperms.api.query.QueryOptions");Object options;if(player!=null)options=contextType.getMethod("getQueryOptions",Object.class).invoke(context,player);else{var live=(java.util.Optional<?>)contextType.getMethod("getQueryOptions",userType).invoke(context,user);options=live.isPresent()?live.get():contextType.getMethod("getStaticQueryOptions").invoke(context);}Object metadata=cachedType.getMethod("getMetaData",queryType).invoke(cached,options);Class<?> metaType=Class.forName("net.luckperms.api.cacheddata.CachedMetaData");Object contextualGroup=metaType.getMethod("getPrimaryGroup").invoke(metadata);if(contextualGroup!=null)result.addProperty("primaryGroup",contextualGroup.toString());
            for(String key:new String[]{"Prefix","Suffix"}){Object v=metaType.getMethod("get"+key).invoke(metadata);if(v!=null)result.addProperty(key.toLowerCase(),v.toString().substring(0,Math.min(512,v.toString().length())));}
            Object permission=cachedType.getMethod("getPermissionData",queryType).invoke(cached,options);Class<?> permissionType=Class.forName("net.luckperms.api.cacheddata.CachedPermissionData");
            for(String key:new String[]{"rivet.admin","rivet.events","rivet.auth.reset","rivet.vote.protected","rivet.stats.edit","rivet.stats.view","rivet.announce","rivet.maintenance","rivet.restart","rivet.reports","rivet.diagnostics"}){Object tristate=permissionType.getMethod("checkPermission",String.class).invoke(permission,key);capabilities.addProperty(key,tristate.toString().equals("TRUE"));}
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
                for(String key:new String[]{"rivet.admin","rivet.events","rivet.auth.reset","rivet.vote.protected","rivet.stats.edit","rivet.stats.view","rivet.announce","rivet.maintenance","rivet.restart","rivet.reports","rivet.diagnostics"})caps.addProperty(key,permissionType.getMethod("checkPermission",String.class).invoke(permission,key).toString().equals("TRUE"));if(caps.get("rivet.admin").getAsBoolean())for(String right:ServerCommands.RIGHTS)caps.addProperty(right,true);row.add("capabilities",caps);rows.add(row);
            }
        }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){throw new IllegalStateException("LuckPerms roles API failed",unavailable);}
        return rows;
    }

}
