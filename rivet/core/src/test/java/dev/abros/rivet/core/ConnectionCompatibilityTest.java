package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import static org.junit.jupiter.api.Assertions.*;
class ConnectionCompatibilityTest {
 @Test void positionPagesAreOptional(){var old=new JsonArray();old.add("map-positions");assertFalse(ConnectionCompatibility.common(old).contains("map-position-pages"));old.add("map-position-pages");assertTrue(ConnectionCompatibility.common(old).contains("map-position-pages"));}
 @Test void newerClientInSameMajorIsAccepted(){assertEquals("",ConnectionCompatibility.failure("1.0.0","1.99.3",WireProtocols.current()));assertFalse(ConnectionCompatibility.failure("1.99.3","1.0.0",WireProtocols.current()).isEmpty());}
 @Test void release210NegotiatesWith203WithoutRequiringNewFeatures(){assertFalse(ConnectionCompatibility.failure("2.1.0","2.0.3",WireProtocols.current()).isEmpty());assertEquals("",ConnectionCompatibility.failure("2.0.3","2.1.0",WireProtocols.current()));var old=new JsonArray();old.add("menu");old.add("players");assertFalse(ConnectionCompatibility.common(old).contains("player-statistics"));assertFalse(ConnectionCompatibility.common(old).contains("moderation-votes"));}
 @Test void differentMajorIsRejectedWithoutAnyProject(){assertTrue(ConnectionCompatibility.failure("2.0.0","1.99.3",WireProtocols.current()).contains("2.x"));}
 @Test void inconsistentWireContractIsRejected(){var protocols=WireProtocols.current();protocols.addProperty("auth",999);assertFalse(ConnectionCompatibility.failure("1.0.0","1.0.1",protocols).isEmpty());}
 @Test void sameReleaseFamilyDoesNotOverrideWireMismatch(){var protocols=WireProtocols.current();protocols.addProperty("menu",999);assertFalse(ConnectionCompatibility.failure("1.1.0","1.0.0",protocols).isEmpty());assertFalse(ConnectionCompatibility.failure(null,"1.0.0",WireProtocols.current()).isEmpty());}
 @Test void unknownOptionalFeaturesAreIgnored(){var features=new JsonArray();features.add("menu");features.add("future-feature");features.add("board");assertEquals(java.util.Set.of("menu","board"),ConnectionCompatibility.common(features));assertFalse(ConnectionCompatibility.common(features).contains("events"));}
 @Test void invalidFeaturesAndVersionsAreRejected(){assertThrows(IllegalArgumentException.class,()->ConnectionCompatibility.common(new JsonObject()));assertThrows(IllegalArgumentException.class,()->ConnectionCompatibility.branch("1.x"));assertFalse(ConnectionCompatibility.failure("1.0.0","",WireProtocols.current()).isEmpty());}
 @Test void release340Keeps330CompatibleWithOptionalSkinOrdering(){assertFalse(ConnectionCompatibility.failure("3.4.0","3.3.0",WireProtocols.current()).isEmpty());assertEquals("",ConnectionCompatibility.failure("3.3.0","3.4.0",WireProtocols.current()));assertEquals("3.x",ConnectionCompatibility.branch("3.4.0"));var old=new JsonArray();old.add("skins");old.add("skin-names");assertFalse(ConnectionCompatibility.common(old).contains("skin-order"));old.add("skin-order");assertTrue(ConnectionCompatibility.common(old).contains("skin-order"));}
 @Test void minimumUsesFullNumericRelease(){
  assertEquals("",ConnectionCompatibility.failure("1.3.2","1.3.2",WireProtocols.current()));
  assertEquals("",ConnectionCompatibility.failure("1.3.2","1.4.0",WireProtocols.current()));
  assertEquals("",ConnectionCompatibility.failure("1.3.2","1.3.10",WireProtocols.current()));
  assertTrue(ConnectionCompatibility.failure("1.3.2","1.3.1",WireProtocols.current()).contains("не ниже 1.3.2"));
  assertFalse(ConnectionCompatibility.failure("1.3.2","2.0.0",WireProtocols.current()).isEmpty());
  assertFalse(Versions.supportsRequirement("1.3.1","1.3.2"));
  assertTrue(Versions.supportsRequirement("1.4.0","1.3.2"));
 }
}
