package dev.abros.rivet.core.map;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MapPublicationStateTest {
 JsonObject row(int id,long revision,boolean removed){var j=new JsonObject();j.addProperty("id",new UUID(0,id).toString());j.addProperty("revision",revision);if(removed)j.addProperty("removed",true);else{j.addProperty("type","marker");j.add("published",new JsonObject());}return j;}
 JsonObject page(long version,boolean delta,boolean more,JsonObject... rows){var j=new JsonObject();j.addProperty("snapshot",version);j.addProperty("delta",delta);var a=new JsonArray();for(var row:rows)a.add(row);j.add("mapObjects",a);if(more)j.add("nextCursor",rows[rows.length-1].get("id"));return j;}
 @Test void replacementIsAtomicAndProgressRenewsLease(){var s=new MapPublicationState();s.page(page(1,false,false,row(1,1,false)),0);s.begin();assertFalse(s.page(page(2,false,true,row(2,2,false)),20000));assertTrue(s.fresh(40000));assertEquals(new UUID(0,1).toString(),s.rows().iterator().next().get("id").getAsString());s.page(page(2,false,false,row(3,2,false)),45000);assertEquals(2,s.rows().size());assertFalse(s.fresh(75001));}
 @Test void withdrawalDuringFullReloadCannotBeResurrectedByOldPage(){var s=new MapPublicationState();s.page(page(1,false,false,row(1,1,false)),0);s.restart();s.page(page(2,false,true,row(1,1,false)),10);s.changed(row(1,3,true));assertTrue(s.rows().isEmpty());s.page(page(2,false,false,row(2,2,false)),20);assertEquals(1,s.rows().size());assertEquals(new UUID(0,2).toString(),s.rows().iterator().next().get("id").getAsString());}
 @Test void deltasPreserveUnchangedObjectsAndIgnoreOutOfOrderLiveEvents(){var s=new MapPublicationState();s.page(page(2,false,false,row(1,1,false),row(2,2,false)),0);s.changed(row(1,4,true));s.changed(row(1,3,false));s.begin();s.page(page(3,true,false,row(1,3,false)),10);assertEquals(1,s.rows().size());s.begin();s.page(page(4,true,false,row(1,4,true)),20);assertEquals(4,s.snapshot());}
 @Test void mixedOrDuplicatePagesAreRejected(){var s=new MapPublicationState();s.page(page(1,false,true,row(1,1,false)),0);assertThrows(IllegalArgumentException.class,()->s.page(page(2,false,false,row(2,2,false)),1));s.begin();s.page(page(1,false,true,row(1,1,false)),0);assertThrows(IllegalArgumentException.class,()->s.page(page(1,false,false,row(1,1,false)),1));}
 @Test void errorsDoNotExtendLease(){var s=new MapPublicationState();s.page(page(1,false,false,row(1,1,false)),0);var error=new JsonObject();error.addProperty("error",true);assertThrows(IllegalArgumentException.class,()->s.page(error,29000));assertFalse(s.fresh(30001));}
}
