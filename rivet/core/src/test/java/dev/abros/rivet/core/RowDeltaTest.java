package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RowDeltaTest {
 private JsonArray rows(String s){return JsonParser.parseString(s).getAsJsonArray();}
 @Test void transmitsOnlyChangedRowsAndPreservesOrderAndDeletes(){var old=rows("[{id:'a',title:'Old'},{id:'b',title:'Same'},{id:'c',title:'Gone'}]");var next=rows("[{id:'b',title:'Same'},{id:'a',title:'New'},{id:'d',title:'Added'}]");var delta=RowDelta.encode(next,RowDelta.known(old));assertEquals(2,delta.getAsJsonArray("rows").size());assertEquals(next,RowDelta.apply(old,delta));}
 @Test void emptyDeltaAndMissingBaseline(){var old=rows("[{id:'a',title:'Same'}]");var d=RowDelta.encode(old,RowDelta.known(old));assertEquals(0,d.getAsJsonArray("rows").size());assertThrows(IllegalArgumentException.class,()->RowDelta.apply(new JsonArray(),d));assertEquals(old,RowDelta.apply(old,d));}
 @Test void acceptsFullPageAfterDisconnectedBaseline(){var next=rows("[{id:'a',title:'Same'}]");assertEquals(next,RowDelta.apply(new JsonArray(),RowDelta.encode(next,new JsonObject())));}
}
