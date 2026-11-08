package dev.abros.rivet.core.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238, SHA-1, six digits, 30 seconds. Verification returns the consumed time step. */
public final class Totp {
    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private Totp(){}
    public static String secret(){return encode(AuthSecrets.random(20));}
    static String encode(byte[] bytes){var out=new StringBuilder();int buffer=0,bits=0;for(byte value:bytes){buffer=(buffer<<8)|(value&255);bits+=8;while(bits>=5){bits-=5;out.append(ALPHABET.charAt((buffer>>>bits)&31));}}if(bits>0)out.append(ALPHABET.charAt((buffer<<(5-bits))&31));return out.toString();}
    private static byte[] decode(String value){
        if(value==null||!value.matches("[A-Z2-7]{32}"))throw new IllegalArgumentException("Invalid TOTP secret");
        byte[] out=new byte[20];int buffer=0,bits=0,index=0;for(char c:value.toCharArray()){buffer=(buffer<<5)|ALPHABET.indexOf(c);bits+=5;if(bits>=8){bits-=8;out[index++]=(byte)(buffer>>>bits);}}return out;
    }
    static String code(String secret,long step,int digits){
        if(step<0||(digits!=6&&digits!=8))throw new IllegalArgumentException("Invalid TOTP parameters");
        byte[] key=decode(secret);
        try{var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(key,"HmacSHA1"));byte[] hash=mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());int offset=hash[hash.length-1]&15;int binary=ByteBuffer.wrap(hash,offset,4).getInt()&0x7fffffff;return String.format(Locale.ROOT,"%0"+digits+"d",binary%(digits==6?1000000:100000000));}
        catch(GeneralSecurityException failure){throw new IllegalStateException("TOTP unavailable",failure);}finally{Arrays.fill(key,(byte)0);}
    }
    public static long verify(String secret,String supplied,long now,long lastStep){
        if(supplied==null||!supplied.matches("[0-9]{6}")||now<0)return -1;
        long current=now/30000,matched=-1;
        for(long step=Math.max(0,current-1);step<=current+1;step++){
            boolean match=MessageDigest.isEqual(code(secret,step,6).getBytes(StandardCharsets.US_ASCII),supplied.getBytes(StandardCharsets.US_ASCII));
            if(match&&step>lastStep)matched=step;
        }return matched;
    }
}
