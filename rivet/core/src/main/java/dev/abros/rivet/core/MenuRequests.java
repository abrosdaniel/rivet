package dev.abros.rivet.core;
import com.google.gson.JsonObject;
/** Compatibility facade for callers; command semantics have one source of truth. */
public final class MenuRequests {
 public static boolean read(JsonObject request){return MenuCommands.describe(request).effect()==MenuCommands.Effect.READ;}
 public static boolean mutation(JsonObject request){return MenuCommands.describe(request).effect()==MenuCommands.Effect.WRITE;}
 /** Position changes have their own permission-aware delivery path. Reads never broadcast changes. */
 public static boolean invalidatesCommunity(JsonObject request){var command=MenuCommands.describe(request);return command.action().equals("community")&&command.effect()==MenuCommands.Effect.WRITE&&!command.operation().startsWith("mapPosition");}
 public static boolean sameRead(JsonObject first,JsonObject second){return read(first)&&read(second)&&UiPayload.same(first,second);}
 private MenuRequests(){}
}
