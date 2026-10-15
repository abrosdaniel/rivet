package dev.abros.rivet.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DomainDataTest {
 @Test void nestedListsCannotBeChangedByTheConsumer(){
  var points=new ArrayList<>(List.of(new DomainData.Point(1,2)));
  var territory=new DomainData.Territory("minecraft:overworld","Area",1,points);points.clear();
  assertEquals(1,territory.points().size());assertThrows(UnsupportedOperationException.class,()->territory.points().clear());
  var items=new ArrayList<>(List.of(territory));var page=new DomainData.Page<>(items,"");items.clear();assertEquals(1,page.items().size());
  var choices=new ArrayList<>(List.of(0));var vote=new DomainCommand.CommunityAction(UUID.randomUUID(),1,DomainCommand.Action.POLL_VOTE,"",choices);choices.clear();assertEquals(List.of(0),vote.choices());
 }
 @Test void existingStatusImplementationsKeepWorkingWithoutDomains(){
  RivetApi old=new RivetApi(){public StatusSnapshot serverStatus(){return null;}public StatusEvents serverStatusEvents(ApiCursor cursor,int limit){return null;}};
  assertEquals(ApiException.Code.MODULE_DISABLED,assertThrows(ApiException.class,old::publicDomains).code());
  assertEquals(ApiException.Code.MODULE_DISABLED,assertThrows(ApiException.class,old::mapLayers).code());
 }
}
