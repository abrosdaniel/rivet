package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import java.util.*;
/** Local home composition, shared by narrow and wide layouts. */
final class HomeLayout {
 static final List<String> KEYS=List.of("announcements","tasks","events","groups");
 private static List<String> order=new ArrayList<>(KEYS);private static Set<String> hidden=new HashSet<>();private static boolean loaded;
 static void load(){if(loaded)return;loaded=true;try{var path=file();if(java.nio.file.Files.exists(path)){var j=Json.read(path);if(j.has("order")){var chosen=new LinkedHashSet<String>();for(var e:j.getAsJsonArray("order"))if(KEYS.contains(e.getAsString()))chosen.add(e.getAsString());chosen.addAll(KEYS);order=new ArrayList<>(chosen);}if(j.has("hidden"))for(var e:j.getAsJsonArray("hidden"))if(KEYS.contains(e.getAsString()))hidden.add(e.getAsString());}}catch(Exception ignored){}}
 private static java.nio.file.Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/home-layout.json");}
 static List<String> order(){load();return List.copyOf(order);}
 static boolean visible(String key){load();return !hidden.contains(key);}
 static String name(String key){return switch(key){case "announcements"->"Объявления";case "tasks"->"Задачи";case "events"->"События";case "groups"->"Объединения";default->key;};}
 static void toggle(String key)throws Exception{load();if(!KEYS.contains(key))return;var previous=new HashSet<>(hidden);if(!hidden.remove(key))hidden.add(key);try{save();}catch(Exception failure){hidden=previous;throw failure;}}
 static void moveTo(String key,int index)throws Exception{load();int at=order.indexOf(key);if(at<0||index<0||index>=order.size()||at==index)return;var previous=new ArrayList<>(order);order.remove(at);order.add(index,key);try{save();}catch(Exception failure){order=previous;throw failure;}}
 static void reset()throws Exception{load();var previousOrder=new ArrayList<>(order);var previousHidden=new HashSet<>(hidden);order=new ArrayList<>(KEYS);hidden.clear();try{save();}catch(Exception failure){order=previousOrder;hidden=previousHidden;throw failure;}}
 private static void save()throws Exception{Json.write(file(),Map.of("order",order,"hidden",hidden));}
 static JsonArray arrange(JsonArray input){load();var list=new ArrayList<JsonObject>();for(var e:input){var row=e.getAsJsonObject();String key=key(row);if(key.isEmpty()||visible(key))list.add(row);}list.sort(Comparator.comparingInt(HomeLayout::urgency).thenComparingInt(r->{int n=order.indexOf(key(r));return n<0?-1:n;}));var out=new JsonArray();list.forEach(out::add);return out;}
 private static String key(JsonObject row){String id=Json.opt(row,"id","");if(id.equals("local:pinned"))return "announcements";return switch(Json.opt(row,"section","")){case "task"->"tasks";case "events"->"events";case "groups"->"groups";default->"";};}
 private static int urgency(JsonObject row){long now=System.currentTimeMillis();if(Json.opt(row,"attention","").equals("Вас пригласили"))return 1;if(Json.opt(row,"id","").equals("local:moderation-vote"))return 0;if(Json.opt(row,"section","").equals("task")&&row.has("dueAt")&&row.get("dueAt").getAsLong()>0&&row.get("dueAt").getAsLong()<now)return 1;if(row.has("startsAt")){long remaining=row.get("startsAt").getAsLong()-now;if(remaining>=0&&remaining<=3600000)return 2;}return 3;}
}
