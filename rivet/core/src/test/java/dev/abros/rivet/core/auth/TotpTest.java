package dev.abros.rivet.core.auth;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class TotpTest {
 @TempDir Path root;
 @Test void rfc6238Sha1Vectors(){String secret=Totp.encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));long[] seconds={59,1111111109,1111111111,1234567890,2000000000,20000000000L};String[] expected={"94287082","07081804","14050471","89005924","69279037","65353130"};for(int i=0;i<seconds.length;i++)assertEquals(expected[i],Totp.code(secret,seconds[i]/30,8));}
 @Test void windowAndReplay(){String secret=Totp.secret();long now=120000;for(long step=3;step<=5;step++){String code=Totp.code(secret,step,6);assertEquals(step,Totp.verify(secret,code,now,-1));assertEquals(-1,Totp.verify(secret,code,now,step));}assertEquals(-1,Totp.verify(secret,Totp.code(secret,2,6),now,-1));assertEquals(-1,Totp.verify(secret,"1234567",now,-1));assertEquals(-1,Totp.verify(secret,null,now,-1));}
 @Test void vaultSurvivesReopenAndRejectsOtherAccountOrPurpose()throws Exception{var vault=AuthFactorVault.open(root);String secret=Totp.secret(),sealed=vault.seal("alice","totp",secret);assertFalse(sealed.contains(secret));assertEquals(secret,AuthFactorVault.open(root).open("alice","totp",sealed));assertThrows(Exception.class,()->vault.open("bob","totp",sealed));assertThrows(Exception.class,()->vault.open("alice","pending",sealed));assertNotEquals(sealed,vault.seal("alice","totp",secret));}
}
