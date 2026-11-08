package dev.abros.rivet.core.auth;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Local key is backed up separately from PostgreSQL. Ciphertexts are bound to account and purpose. */
public final class AuthFactorVault {
    private final byte[] key;
    private AuthFactorVault(byte[] key){if(key.length!=32)throw new IllegalArgumentException("Invalid Auth factor key");this.key=key.clone();}
    public static AuthFactorVault open(Path directory)throws Exception{
        Files.createDirectories(directory);Path path=directory.resolve("auth-factors.key");
        if(Files.isSymbolicLink(directory)||Files.isSymbolicLink(path))throw new java.io.IOException("Unsafe Auth factor key path");
        if(!Files.exists(path)){
            Path temp=Files.createTempFile(directory,"auth-factors-",".tmp");
            try{AuthTls.privateFile(temp);Files.write(temp,AuthSecrets.random(32));Files.move(temp,path);}finally{Files.deleteIfExists(temp);}
        }
        AuthTls.privateFile(path);if(Files.size(path)!=32)throw new java.io.IOException("Invalid Auth factor key length");return new AuthFactorVault(Files.readAllBytes(path));
    }
    public String seal(String owner,String purpose,String secret)throws GeneralSecurityException{
        byte[] iv=AuthSecrets.random(12);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));cipher.updateAAD((owner+":"+purpose).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(cipher.doFinal(secret.getBytes(StandardCharsets.US_ASCII)));
    }
    public String open(String owner,String purpose,String sealed)throws GeneralSecurityException{
        try{String[] parts=sealed.split(":",-1);if(parts.length!=2)throw new GeneralSecurityException("Invalid factor ciphertext");byte[] iv=Base64.getDecoder().decode(parts[0]);if(iv.length!=12)throw new GeneralSecurityException("Invalid factor nonce");var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));cipher.updateAAD((owner+":"+purpose).getBytes(StandardCharsets.UTF_8));return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.US_ASCII);}
        catch(IllegalArgumentException invalid){throw new GeneralSecurityException("Invalid factor ciphertext",invalid);}
    }
}
