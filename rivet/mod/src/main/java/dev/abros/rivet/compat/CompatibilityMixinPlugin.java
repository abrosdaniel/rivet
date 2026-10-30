package dev.abros.rivet.compat;
import org.spongepowered.asm.mixin.extensibility.*;
import org.objectweb.asm.tree.ClassNode;
import java.util.*;
/** Only probes resources at mixin discovery time: ModList is not initialized yet. */
public final class CompatibilityMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){} public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){
  if(mixin.contains("Envelope"))return envelopeSupported();
  return getClass().getClassLoader().getResource(target.replace('.','/')+".class")!=null;
 }
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
