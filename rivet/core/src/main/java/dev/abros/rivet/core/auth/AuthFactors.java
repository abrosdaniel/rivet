package dev.abros.rivet.core.auth;

import dev.abros.rivet.core.PgDatabase;
import java.util.*;

/** Internal factor persistence. Caller must hold the account auth lock and enforce attempt limits. */
final class AuthFactors {
    private final PgDatabase db;private final AuthFactorVault vault;
    AuthFactors(PgDatabase db,AuthFactorVault vault){this.db=db;this.vault=vault;}
    private void transaction(){if(!db.inTransaction())throw new IllegalStateException("Factor operation outside authentication transaction");}
    boolean enabled(String uuid)throws Exception{transaction();try(var q=db.connection().prepareStatement("SELECT secret IS NOT NULL FROM auth_factors WHERE account=?")){q.setString(1,uuid);try(var r=q.executeQuery()){return r.next()&&r.getBoolean(1);}}}
    String begin(String uuid,long now)throws Exception{
        transaction();if(enabled(uuid))throw new IllegalArgumentException("Second factor already enabled");
        String secret=Totp.secret(),sealed=vault().seal(uuid,"pending",secret);
        try(var q=db.connection().prepareStatement("INSERT INTO auth_factors(account,pending,pending_until) VALUES(?,?,?) ON CONFLICT(account) DO UPDATE SET pending=excluded.pending,pending_until=excluded.pending_until")){q.setString(1,uuid);q.setString(2,sealed);q.setLong(3,now+600000);q.executeUpdate();}return secret;
    }
    List<String> confirm(String uuid,String code,long now)throws Exception{
        transaction();String secret;long step;
        try(var q=db.connection().prepareStatement("SELECT pending,pending_until,secret FROM auth_factors WHERE account=? FOR UPDATE")){q.setString(1,uuid);try(var r=q.executeQuery()){
            if(!r.next()||r.getString(1)==null||r.getLong(2)<=now||r.getString(3)!=null)return null;
            secret=vault().open(uuid,"pending",r.getString(1));step=Totp.verify(secret,code,now,-1);if(step<0)return null;
        }}
        try(var q=db.connection().prepareStatement("UPDATE auth_factors SET secret=?,pending=NULL,pending_until=0,last_step=? WHERE account=?")){q.setString(1,vault().seal(uuid,"totp",secret));q.setLong(2,step);q.setString(3,uuid);q.executeUpdate();}
        return backupCodes(uuid);
    }
    boolean verify(String uuid,String code,long now)throws Exception{
        transaction();try(var q=db.connection().prepareStatement("SELECT secret,last_step FROM auth_factors WHERE account=? FOR UPDATE")){q.setString(1,uuid);try(var r=q.executeQuery()){
            if(!r.next()||r.getString(1)==null)return true;
            if(code==null)return false;
            if(code.matches("[A-Za-z0-9_-]{22}")){
                try(var consume=db.connection().prepareStatement("DELETE FROM auth_backup_codes WHERE account=? AND digest=?")){consume.setString(1,uuid);consume.setString(2,AuthSecrets.digest(uuid+":"+code));return consume.executeUpdate()==1;}
            }
            long step=Totp.verify(vault().open(uuid,"totp",r.getString(1)),code,now,r.getLong(2));if(step<0)return false;
            try(var consume=db.connection().prepareStatement("UPDATE auth_factors SET last_step=? WHERE account=?")){consume.setLong(1,step);consume.setString(2,uuid);consume.executeUpdate();}return true;
        }}
    }
    List<String> backupCodes(String uuid)throws Exception{
        transaction();try(var q=db.connection().prepareStatement("DELETE FROM auth_backup_codes WHERE account=?")){q.setString(1,uuid);q.executeUpdate();}
        var result=new ArrayList<String>();try(var q=db.connection().prepareStatement("INSERT INTO auth_backup_codes(account,digest) VALUES(?,?)")){
            for(int i=0;i<10;i++){String code=Base64.getUrlEncoder().withoutPadding().encodeToString(AuthSecrets.random(16));result.add(code);q.setString(1,uuid);q.setString(2,AuthSecrets.digest(uuid+":"+code));q.addBatch();}q.executeBatch();
        }return List.copyOf(result);
    }
    void remove(String uuid)throws Exception{transaction();for(String table:List.of("auth_backup_codes","auth_factors"))try(var q=db.connection().prepareStatement("DELETE FROM "+table+" WHERE account=?")){q.setString(1,uuid);q.executeUpdate();}}
    private AuthFactorVault vault(){if(vault==null)throw new IllegalStateException("Auth factor key unavailable");return vault;}
}
