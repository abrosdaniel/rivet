package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CommunityPolicyTest {
 @Test void archivedMechanicsOfferNoMutationsEvenToAdministrators(){
  var admin=new CommunityStore.Actor("owner","Owner",true,true);
  for(String section:List.of("board","events","polls","ideas","tasks","storage")){
   var archived=new JsonObject();archived.addProperty("section",section);
   assertTrue(CommunityPolicy.actions(archived,admin,0).isEmpty(),section);
   for(String action:List.of("edit","delete","restore","vote","join","location"))
    assertThrows(CommunityFailure.class,()->CommunityPolicy.check(archived,admin,action,0),section+"/"+action);
  }
 }
 @Test void assistantManagesApplicationsButCannotTransferLeadership(){
  var group=group();group.getAsJsonObject("members").addProperty("assistant","assistant");
  var actions=CommunityPolicy.actions(group,new CommunityStore.Actor("assistant","Assistant",false,false),0);
  for(String action:List.of("application","invite","recruiting","leave"))assertTrue(actions.contains(new JsonPrimitive(action)),action);
  for(String action:List.of("role","edit","delete","hide"))assertFalse(actions.contains(new JsonPrimitive(action)),action);
 }
 @Test void pendingApplicantCanWithdrawWithoutSubmittingDuplicateApplication(){
  var group=group();group.getAsJsonObject("applications").add("applicant",new JsonObject());
  var actor=new CommunityStore.Actor("applicant","Applicant",false,false);
  assertTrue(CommunityPolicy.actions(group,actor,0).contains(new JsonPrimitive("withdrawApplication")));
  assertThrows(CommunityFailure.class,()->CommunityPolicy.check(group,actor,"apply",0));
 }
 @Test void restoreUsesConfiguredRetentionAndSameBoundaryAsPurge(){
  var group=group();group.addProperty("status","deleted");group.addProperty("deletedAt",1000);
  var owner=new CommunityStore.Actor("owner","Owner",false,false);long day=86400000L;
  assertDoesNotThrow(()->CommunityPolicy.check(group,owner,"restore",1000+40*day,60));
  assertThrows(CommunityFailure.class,()->CommunityPolicy.check(group,owner,"restore",1000+8*day,7));
  assertDoesNotThrow(()->CommunityPolicy.check(group,owner,"restore",1000+7*day-1,7));
  assertThrows(CommunityFailure.class,()->CommunityPolicy.check(group,owner,"restore",1000+7*day,7));
  assertThrows(CommunityFailure.class,()->CommunityPolicy.check(group,new CommunityStore.Actor("stranger","Stranger",false,false),"restore",1001,60));
 }
 private static JsonObject group(){
  var group=new JsonObject();group.addProperty("section","groups");group.addProperty("owner","owner");
  group.addProperty("status","open");group.addProperty("recruiting",true);
  for(String key:List.of("members","applications","invitations"))group.add(key,new JsonObject());
  group.getAsJsonObject("members").addProperty("owner","leader");return group;
 }
}
