package dev.abros.rivet.core;

import com.google.gson.*;
import dev.abros.rivet.core.map.MapPlaceSnapshot;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MapPlaceSnapshotTest {
 private static JsonObject page(int first,int count,boolean more){var out=new JsonObject();var rows=new JsonArray();for(int i=first;i<first+count;i++){var row=new JsonObject();row.addProperty("id",new UUID(0,i).toString());row.addProperty("title","Place "+i);rows.add(row);}out.add("places",rows);if(more)out.addProperty("nextCursor",new UUID(0,first+count-1).toString());return out;}
 @Test void atomicPagesAndStableDerivedCache(){var state=new MapPlaceSnapshot();state.begin();state.accept(page(1,1,false),0);var before=state.rows(1);state.begin();assertFalse(state.accept(page(2,20,true),2).isEmpty());assertSame(before,state.rows(3));state.accept(page(22,1,false),4);var after=state.rows(5);assertEquals(21,after.size());assertNotSame(before,after);state.begin();state.accept(page(2,20,true),6);state.accept(page(22,1,false),7);assertSame(after,state.rows(8));}
 @Test void failedRefreshDoesNotExtendAccess(){var state=new MapPlaceSnapshot();state.begin();state.accept(page(1,1,false),0);state.begin();state.accept(page(2,20,true),30000);assertThrows(IllegalArgumentException.class,()->state.accept(page(2,20,true),31000));assertEquals(1,state.rows(59999).size());assertTrue(state.rows(60000).isEmpty());assertThrows(IllegalStateException.class,()->state.accept(page(22,1,false),60001));}
 @Test void invalidCursorAndOversizedPageNeverCommit(){var state=new MapPlaceSnapshot();state.begin();var bad=page(1,1,true);bad.addProperty("nextCursor",new UUID(0,7).toString());assertThrows(IllegalArgumentException.class,()->state.accept(bad,0));assertTrue(state.rows(1).isEmpty());state.begin();assertThrows(IllegalArgumentException.class,()->state.accept(page(1,21,false),2));state.begin();assertThrows(IllegalArgumentException.class,()->state.accept(new JsonObject(),3));}
 @Test void boundsResetAndEmptyReplacement(){var state=new MapPlaceSnapshot();state.begin();for(int p=0;p<49;p++)assertFalse(state.accept(page(1+p*20,20,true),p).isEmpty());assertTrue(state.accept(page(981,20,true),49).isEmpty());assertEquals(1000,state.rows(50).size());state.begin();state.accept(page(1,0,false),51);assertTrue(state.rows(52).isEmpty());state.begin();state.accept(page(1,1,false),53);state.clear();assertTrue(state.rows(54).isEmpty());}
}
