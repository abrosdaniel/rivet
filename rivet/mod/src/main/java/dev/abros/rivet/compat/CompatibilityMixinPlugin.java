package dev.abros.rivet.compat;
import org.spongepowered.asm.mixin.extensibility.*;
import org.objectweb.asm.tree.ClassNode;
import java.util.*;
/** Only probes resources at mixin discovery time: ModList is not initialized yet. */
public final class CompatibilityMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){} public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){
  if(mixin.contains("Indexor"))return indexorSupported();
  if(mixin.contains("BrewinChat"))return brewinSupported(target);
  if(mixin.contains("Envelope"))return envelopeSupported();
  return getClass().getClassLoader().getResource(target.replace('.','/')+".class")!=null;
 }
 private static Boolean indexorCompatible;
 public static synchronized boolean indexorSupported(){
  if(indexorCompatible!=null)return indexorCompatible;
  try {
   var expected=Map.ofEntries(Map.entry("ledger/events/LedgerEventHandler","22a08bf150bda91c982e96c09b25766133b07417add1529499b73c398ea3fd41"),
   Map.entry("ledger/core/LedgerManager","941f5d1f70d54a909cfe035c7712ff5de2c4cd97949d4eeb308b20464a6b2c7c"),
   Map.entry("ledger/core/LogEntry","9efdf4c1de0ee85e00b1d3089f3d309d8ba2f21e1b4e39106a98681feab594c4"),
   Map.entry("ledger/commands/InspectCommand","91938c9f958e67f8e0a9634ca5a4383d5c46c1b9fbcb66ef6c52210281c3d277"),
   Map.entry("ledger/storage/SQLiteStorage","c129a935e0793061ee2f6a31b22d138b5b55421ff433b55f7f87086dda4e52da"),
   Map.entry("ledger/storage/JsonStorage","492f8514953bfeca320acadf74d87838b143285cfe255df3a597a8f4065370ef"));
   for(var entry:expected.entrySet())try(var in=CompatibilityMixinPlugin.class.getClassLoader().getResourceAsStream(entry.getKey()+".class")){
    if(in==null||!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(entry.getValue()))return indexorCompatible=false;
   }
   return indexorCompatible=true;
  }catch(Exception unavailable){return indexorCompatible=false;}
 }
 private boolean brewinSupported(String target){try{return shape(target.replace('.','/')).methods.stream().anyMatch(m->m.name.equals("iCanHear")&&m.desc.equals("(Lnet/neoforged/neoforge/client/event/ClientChatReceivedEvent$Player;)V"));}catch(Exception absent){return false;}}
 private boolean envelopeSupported(){try{
  String base="io/github/mortuusars/envelope/world/";
  var address=shape(base+"mail/address/type/PlayerAddress");var known=shape(base+"KnownPlayers");var service=shape(base+"mail/MailService");
  return address.fields.stream().anyMatch(f->f.name.equals("CODEC")&&f.desc.equals("Lcom/mojang/serialization/MapCodec;"))
   &&address.methods.stream().anyMatch(m->m.name.equals("<init>")&&m.desc.equals("(Ljava/lang/String;)V"))
   &&known.methods.stream().anyMatch(m->m.name.equals("update")&&m.desc.equals("(Lnet/minecraft/world/entity/player/Player;Ljava/util/function/Consumer;)V"))
   &&known.methods.stream().anyMatch(m->m.name.equals("getDefaultAddressOf")&&m.desc.equals("(L"+base+"mail/address/type/PlayerAddress;)Ljava/util/Optional;"))
   &&service.methods.stream().anyMatch(m->m.name.equals("getPlayerDefaultAddress")&&m.desc.equals("(L"+base+"mail/address/type/PlayerAddress;)Ljava/util/Optional;"));
 }catch(Exception unavailable){return false;}}
 private ClassNode shape(String name)throws java.io.IOException{try(var stream=getClass().getClassLoader().getResourceAsStream(name+".class")){if(stream==null)throw new java.io.IOException("Optional class absent");var node=new ClassNode();new org.objectweb.asm.ClassReader(stream).accept(node,org.objectweb.asm.ClassReader.SKIP_CODE|org.objectweb.asm.ClassReader.SKIP_DEBUG|org.objectweb.asm.ClassReader.SKIP_FRAMES);return node;}}

 public void acceptTargets(Set<String>a,Set<String>b){} public List<String>getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
