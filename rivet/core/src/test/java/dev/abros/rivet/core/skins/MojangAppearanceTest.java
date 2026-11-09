package dev.abros.rivet.core.skins;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class MojangAppearanceTest {
 private static byte[] profile(String textures){String encoded=Base64.getEncoder().encodeToString(("{\"textures\":"+textures+"}").getBytes(StandardCharsets.UTF_8));return ("{\"properties\":[{\"name\":\"textures\",\"value\":\""+encoded+"\"}]}").getBytes(StandardCharsets.UTF_8);}
 @Test void downloadsSkinAndCapeFromSameProfile()throws Exception{
  var urls=new ArrayList<String>();var result=MojangSkins.profile(profile("{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/aa\",\"metadata\":{\"model\":\"slim\"}},\"CAPE\":{\"url\":\"https://textures.minecraft.net/texture/bb\"}}"),(url,limit)->{urls.add(url);assertEquals(1048576,limit);return url.getBytes(StandardCharsets.UTF_8);});
  assertTrue(result.slim());assertEquals(2,urls.size());assertTrue(urls.stream().allMatch(u->u.startsWith("https://")));assertArrayEquals(urls.get(1).getBytes(StandardCharsets.UTF_8),result.cape());assertNotNull(result.png());
 }
 @Test void capeDoesNotRequireCustomSkin()throws Exception{var result=MojangSkins.profile(profile("{\"CAPE\":{\"url\":\"https://textures.minecraft.net/texture/aa\"}}"),(u,l)->new byte[]{1});assertNull(result.png());assertArrayEquals(new byte[]{1},result.cape());}
 @Test void unavailableTextureIsFailureNotAbsentCape(){assertThrows(IOException.class,()->MojangSkins.profile(profile("{\"CAPE\":{\"url\":\"https://textures.minecraft.net/texture/aa\"}}"),(u,l)->null));}
 @Test void rejectsForeignTextureHostsBeforeFetch(){assertThrows(IOException.class,()->MojangSkins.profile(profile("{\"CAPE\":{\"url\":\"https://example.org/texture/aa\"}}"),(u,l)->{fail("foreign fetch");return null;}));}
}
