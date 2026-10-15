package dev.abros.rivet.core.api;

import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.core.auth.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.Semaphore;
import static dev.abros.rivet.api.ApiException.Code.*;

/** Bounded, key-bound backend sessions. Only hashes are retained; restart invalidates all tokens. */
public final class PlayerSessions implements AutoCloseable {
    public record Player(String uuid,String name,long generation) {}
    public record Issued(String token,long expires,Player player) { @Override public String toString(){return "Issued[redacted]";} }
    public record Challenge(String code,String pollToken,long expires,String application) { @Override public String toString(){return "Challenge[redacted]";} }
    private record Session(UUID key,String scope,Player player,long expires) {}
    private record Pending(UUID key,String app,String pollHash,long expires,Player player) {}
    private final Map<String,Session> sessions=new HashMap<>();
    private final Map<String,Pending> pending=new HashMap<>();
    private final ApiKeys keys;private final AuthStore auth;private final boolean enabled;
    private final Clock clock;private final long lifetime;private final int limit;
    private final Semaphore passwords=new Semaphore(2);private boolean closed;
    public PlayerSessions(ApiKeys keys,AuthStore auth,boolean enabled,Clock clock,int hours,int limit){
        if(hours<1||hours>720||limit<1||limit>10000)throw new IllegalArgumentException("Invalid session limits");
        this.keys=keys;this.auth=auth;this.enabled=enabled;this.clock=clock;this.lifetime=hours*3600000L;this.limit=limit;
    }
    private void clean(){if(closed)throw new ApiException(UNAVAILABLE,"Sessions stopped");long now=clock.millis();sessions.values().removeIf(s->s.expires<=now);pending.values().removeIf(s->s.expires<=now);}
    private static ApiException denied(){return new ApiException(UNAUTHENTICATED,"Invalid session or credentials");}
    public Issued login(ApiKeys.Grant grant,String name,char[] password)throws Exception{
        try{
            if(!enabled)throw new ApiException(FORBIDDEN,"Password authentication disabled");keys.revalidate(grant,"sessions.password");
            if(!passwords.tryAcquire())throw new ApiException(BUSY,"Login busy");
            AuthStore.Account account;
            try{account=auth.apiLogin(name,password,clock.millis());}catch(IllegalArgumentException invalid){throw denied();}finally{passwords.release();}
            keys.revalidate(grant,"sessions.password");
            return issue(grant,"sessions.password",new Player(account.uuid(),account.name(),account.generation()));
        }finally{Arrays.fill(password,'\0');}
    }
    private synchronized Issued issue(ApiKeys.Grant grant,String scope,Player player){
        clean();if(sessions.size()>=limit||sessions.values().stream().filter(s->s.key.equals(grant.keyId())&&s.player.uuid.equals(player.uuid)).count()>=10)throw new ApiException(BUSY,"Session capacity");
        String token=AuthSecrets.token();long expires=clock.millis()+lifetime;sessions.put(AuthSecrets.digest(token),new Session(grant.keyId(),scope,player,expires));return new Issued(token,expires,player);
    }
    public Challenge challenge(ApiKeys.Grant grant)throws Exception{
        if(enabled)throw new ApiException(FORBIDDEN,"Use password login");keys.revalidate(grant,"sessions.link");
        synchronized(this){clean();
        if(pending.size()>=Math.min(limit,256)||pending.values().stream().filter(p->p.key.equals(grant.keyId())).count()>=8)throw new ApiException(BUSY,"Link capacity");
        String code=HexFormat.of().formatHex(AuthSecrets.random(16)),poll=AuthSecrets.token();long expires=clock.millis()+300000;
        pending.put(AuthSecrets.digest(code),new Pending(grant.keyId(),grant.application(),AuthSecrets.digest(poll),expires,null));return new Challenge(code,poll,expires,grant.application());}
    }
    /** Called only by the game adapter for the actual authenticated command sender. */
    public synchronized void confirm(String code,String application,String uuid,String name){
        clean();if(enabled)throw new ApiException(FORBIDDEN,"Linking disabled");
        String hash=AuthSecrets.digest(code);var p=pending.get(hash);if(p==null||p.player!=null||!p.app.equals(application))throw denied();
        UUID.fromString(uuid);pending.put(hash,new Pending(p.key,p.app,p.pollHash,p.expires,new Player(uuid,name,-1)));
    }
    public Issued exchange(ApiKeys.Grant grant,String code,String poll)throws Exception{
        keys.revalidate(grant,"sessions.link");synchronized(this){clean();String hash=AuthSecrets.digest(code);var p=pending.get(hash);
        if(p==null||!p.key.equals(grant.keyId())||!p.pollHash.equals(AuthSecrets.digest(poll)))throw denied();
        if(p.player==null)return null;
        var result=issue(grant,"sessions.link",p.player);pending.remove(hash);return result;}
    }
    public Player validate(ApiKeys.Grant grant,String token)throws Exception{
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw denied();
        String hash=AuthSecrets.digest(token);Session s;synchronized(this){clean();s=sessions.get(hash);}
        if(s==null||!s.key.equals(grant.keyId()))throw denied();keys.revalidate(grant,s.scope);
        if(enabled){var a=auth.account(s.player.name);if(a==null||a.blocked()||a.generation()!=s.player.generation||!a.uuid().equals(s.player.uuid))throw denied();}
        // Authorization may block on storage while the session is revoked or expires.
        synchronized(this){clean();if(sessions.get(hash)!=s)throw denied();return s.player;}
    }
    public synchronized void revoke(ApiKeys.Grant grant,String token){clean();var s=sessions.get(AuthSecrets.digest(token));if(s==null||!s.key.equals(grant.keyId()))throw denied();sessions.remove(AuthSecrets.digest(token));}
    public synchronized void revokePlayer(String uuid){clean();sessions.values().removeIf(s->s.player.uuid.equals(uuid));pending.values().removeIf(p->p.player!=null&&p.player.uuid.equals(uuid));}
    @Override public synchronized void close(){closed=true;sessions.clear();pending.clear();}
}
