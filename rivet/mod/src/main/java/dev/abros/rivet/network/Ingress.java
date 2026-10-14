package dev.abros.rivet.network;

import dev.abros.rivet.core.IngressBudget;
import io.netty.util.AttributeKey;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Called on the network thread before parsing JSON or queuing game-thread work. */
final class Ingress {
 private static final AttributeKey<IngressBudget> KEY=AttributeKey.valueOf("rivet:ingress");
 static void dispatch(String json,IPayloadContext context,Runnable work){
  dispatch(json.length(),context,work);
 }
 static void dispatch(int characters,IPayloadContext context,Runnable work){
  var attribute=context.connection().channel().attr(KEY);var budget=attribute.get();if(budget==null){var fresh=new IngressBudget();var old=attribute.setIfAbsent(fresh);budget=old==null?fresh:old;}
  if(!budget.acquire(characters)){context.disconnect(net.minecraft.network.chat.Component.translatable("rivet.network.request_limit"));return;}
  var reserved=budget;try{context.enqueueWork(()->{if(context.connection().isConnected())work.run();}).whenComplete((ignored,failure)->reserved.release());}catch(RuntimeException failure){reserved.release();throw failure;}
 }
}
