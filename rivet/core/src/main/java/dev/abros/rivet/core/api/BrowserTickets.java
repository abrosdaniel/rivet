package dev.abros.rivet.core.api;

import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.core.ServerSettings;
import dev.abros.rivet.core.auth.AuthSecrets;
import java.time.Clock;
import java.util.*;
import static dev.abros.rivet.api.ApiException.Code.*;

/** One-use admission credentials. No permanent key is stored or delegated to a browser. */
public final class BrowserTickets implements AutoCloseable {
 public record Limits(int seconds,int total,int perKey){
  public Limits{if(seconds<5||seconds>120||total<1||total>10000||perKey<1||perKey>total)throw new IllegalArgumentException("Invalid WebSocket ticket limits");}
  public static Limits from(ServerSettings s){return new Limits(s.number("api.websocket.ticketSeconds"),s.number("api.websocket.ticketLimit"),s.number("api.websocket.ticketsPerKey"));}
 }
 public record Issued(String ticket,long expiresAtEpochMillis){@Override public String toString(){return "BrowserTicket[redacted]";}}
 public static final class Admission {
  private final ApiKeys.Grant grant;private final String session;
  private Admission(ApiKeys.Grant grant,String session){this.grant=grant;this.session=session;}
  ApiKeys.Grant grant(){return grant;}String session(){return session;}
  @Override public String toString(){return "BrowserAdmission[redacted]";}
 }
 private record Pending(Admission admission,String origin,long expires){}
 private final ApiKeys keys;private final PlayerSessions sessions;private final Clock clock;private final Limits limits;private final Set<String> origins;
 private final Map<String,Pending> pending=new HashMap<>();private boolean closed;
 public BrowserTickets(ApiKeys keys,PlayerSessions sessions,Clock clock,Limits limits,Set<String> origins){this.keys=keys;this.sessions=sessions;this.clock=clock;this.limits=limits;this.origins=Set.copyOf(origins);}
 private void clean(){if(closed)throw new ApiException(UNAVAILABLE,"Browser tickets stopped");pending.values().removeIf(p->p.expires<=clock.millis());}
 public Issued issue(ApiKeys.Grant grant,String session,String origin)throws Exception{
  if(!origins.contains(origin))throw new ApiException(FORBIDDEN,"Origin unavailable");var admission=new Admission(grant,session);validate(admission);
  synchronized(this){clean();if(pending.size()>=limits.total||pending.values().stream().filter(p->p.admission.grant.keyId().equals(grant.keyId())).count()>=limits.perKey)throw new ApiException(BUSY,"Ticket capacity");
   String token=AuthSecrets.token();long expires=clock.millis()+limits.seconds*1000L;pending.put(AuthSecrets.digest(token),new Pending(admission,origin,expires));return new Issued(token,expires);
  }
 }
 public Admission consume(String token,String origin)throws Exception{
  if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw denied();Pending value;
  synchronized(this){clean();value=pending.remove(AuthSecrets.digest(token));}
  if(value==null||!value.origin.equals(origin)||!origins.contains(origin))throw denied();validate(value.admission);return value.admission;
 }
 public void validate(Admission admission)throws Exception{
  synchronized(this){clean();}keys.revalidate(admission.grant,"websocket.connect");keys.revalidate(admission.grant,"websocket.ticket");sessions.validate(admission.grant,admission.session);synchronized(this){clean();}
 }
 private static ApiException denied(){return new ApiException(UNAUTHENTICATED,"Invalid browser ticket");}
 @Override public synchronized void close(){closed=true;pending.clear();}
}
