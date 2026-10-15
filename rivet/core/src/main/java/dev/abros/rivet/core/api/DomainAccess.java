package dev.abros.rivet.core.api;
import dev.abros.rivet.api.ApiException;
import java.util.UUID;
/** Server-owned current permissions; never supplied in a network payload. Runs on bounded API workers. */
@FunctionalInterface public interface DomainAccess {
 boolean allowed(UUID player,String permission)throws Exception;
 DomainAccess DENY=(player,permission)->false;
 default void require(String player,String permission)throws Exception{if(!allowed(UUID.fromString(player),permission))throw new ApiException(ApiException.Code.FORBIDDEN,"Permission required");}
}
