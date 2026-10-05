package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskDetailPageTest {
 private JsonObject task(){var t=new JsonObject();var stages=new JsonArray();var comments=new JsonArray();for(int i=0;i<500;i++){var stage=new JsonObject();stage.addProperty("title","Stage "+i);stages.add(stage);var comment=new JsonObject();comment.addProperty("text","\\".repeat(500));comments.add(comment);}t.add("subtasks",stages);t.add("comments",comments);return t;}
 @Test void pagesKeepEveryEntryAccessibleAndBoundEncodedSize(){var source=task();var request=new JsonObject();request.addProperty("commentOffset",0);int offset=0,seen=0;while(offset<500){var page=source.deepCopy();request.addProperty("commentOffset",offset);TaskDetailPage.apply(page,request);assertTrue(page.toString().length()<30000);assertEquals(500,page.get("commentCount").getAsInt());int size=page.getAsJsonArray("comments").size();assertTrue(size>0);offset+=size;seen+=size;}assertEquals(500,seen);assertEquals(500,source.getAsJsonArray("comments").size());}
 @Test void stageIndicesAndLastCommentsPageRemainStable(){var t=task();var request=new JsonObject();request.addProperty("subtaskOffset",40);TaskDetailPage.apply(t,request);assertEquals("Stage 40",Json.str(t.getAsJsonArray("subtasks").get(0).getAsJsonObject(),"title"));assertEquals(40,t.get("subtaskOffset").getAsInt());assertEquals(480,t.get("commentOffset").getAsInt());}
 @Test void largeHistoryArraysAreSummarizedInResponseOnly(){var t=task();var history=new JsonArray();var event=new JsonObject();var changes=new JsonArray();var change=new JsonObject();change.add("before",t.get("subtasks").deepCopy());change.add("after",t.get("subtasks").deepCopy());changes.add(change);event.add("changes",changes);history.add(event);t.add("history",history);TaskDetailPage.apply(t,new JsonObject());assertEquals("Записей: 500",Json.str(change,"before"));assertTrue(t.toString().length()<30000);}
}
