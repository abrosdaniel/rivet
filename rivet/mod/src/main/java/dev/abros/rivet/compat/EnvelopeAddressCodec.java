package dev.abros.rivet.compat;
import com.mojang.serialization.*;
import java.util.UUID;
import java.util.stream.Stream;
/** Extends saved addresses without changing Envelope's on-wire codec or existing fields. */
public final class EnvelopeAddressCodec extends MapCodec<Object> {
 private final MapCodec<Object> delegate;
 public EnvelopeAddressCodec(MapCodec<Object> delegate){this.delegate=delegate;}
 public <T> Stream<T> keys(DynamicOps<T> ops){return Stream.concat(delegate.keys(ops),Stream.of(ops.createString("rivet_owner")));}
 public <T> DataResult<Object> decode(DynamicOps<T> ops,MapLike<T> input){
  return delegate.decode(ops,input).flatMap(address->{
   if(!(address instanceof EnvelopeAddressOwner owner))return DataResult.error(()->"Envelope identity bridge missing");
   T saved=input.get("rivet_owner");owner.rivet$legacy(saved==null);owner.rivet$owner(null);
   if(saved!=null){try{owner.rivet$owner(UUID.fromString(ops.getStringValue(saved).getOrThrow()));}catch(Exception invalid){return DataResult.error(()->"Invalid Envelope owner UUID");}}
   return DataResult.success(address);
  });
 }
 public <T> RecordBuilder<T> encode(Object address,DynamicOps<T> ops,RecordBuilder<T> prefix){
  var result=delegate.encode(address,ops,prefix);UUID owner=EnvelopeBindings.owner(address);
  return owner==null?result:result.add("rivet_owner",ops.createString(owner.toString()));
 }
}
