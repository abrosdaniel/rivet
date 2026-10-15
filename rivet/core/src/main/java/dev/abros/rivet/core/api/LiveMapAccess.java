package dev.abros.rivet.core.api;

import com.google.gson.JsonObject;
import dev.abros.rivet.api.ApiException;
import java.util.Map;
import java.util.UUID;

/** Runtime-owned, player-scoped live reads. No stored coordinate history. */
@FunctionalInterface
public interface LiveMapAccess {
 default java.util.Set<String> capabilities(){return java.util.Set.of();}
 JsonObject read(UUID viewer,String resource,Map<String,String> query)throws Exception;
 LiveMapAccess UNAVAILABLE=(viewer,resource,query)->{throw new ApiException(ApiException.Code.UNAVAILABLE,"Live map unavailable");};
}
