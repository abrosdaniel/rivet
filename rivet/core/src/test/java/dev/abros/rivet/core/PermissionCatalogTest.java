package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PermissionCatalogTest {
 @Test void onlyRegisteredNodesCanBeEdited(){assertEquals(PermissionCatalog.nodes().size(),new java.util.HashSet<>(PermissionCatalog.nodes()).size());assertThrows(IllegalArgumentException.class,()->PermissionCatalog.require("minecraft.command.op"));assertThrows(IllegalArgumentException.class,()->PermissionCatalog.require("rivet.*"));}
 @Test void sensitiveCapabilitiesRemainSeparate(){for(String node:java.util.List.of("rivet.permissions.manage","rivet.map.teleport","rivet.auth.reset"))assertFalse(PermissionCatalog.require(node).adminDefault());assertTrue(PermissionCatalog.require("rivet.map.seeHiddenPlayers").adminDefault());}
 @Test void roleEditsUseMutationTransport(){var input=new com.google.gson.JsonObject();input.addProperty("action","rolePermission");assertTrue(MenuRequests.mutation(input));assertFalse(MenuRequests.read(input));}
}
