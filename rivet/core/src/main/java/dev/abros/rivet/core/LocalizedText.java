package dev.abros.rivet.core;

import com.google.gson.*;
import java.util.List;
import java.util.ArrayList;

/** Explicit translation fragments. Literal player content is never interpreted as a key. */
public final class LocalizedText {
    private record Part(String key,String literal) {}
    private final List<Part> parts;
    private LocalizedText(List<Part> parts){this.parts=List.copyOf(parts);}
    public static LocalizedText key(String key){return new LocalizedText(List.of(new Part(key,null)));}
    public static LocalizedText literal(String value){return new LocalizedText(List.of(new Part(null,value)));}
    public static LocalizedText parts(LocalizedText... fragments){
        var parts=new ArrayList<Part>();for(var fragment:fragments)parts.addAll(fragment.parts);
        return new LocalizedText(parts);
    }
    public String render(){var out=new StringBuilder();for(var part:parts)out.append(part.key==null?part.literal:Messages.text(part.key));return out.toString();}
    public void put(JsonObject target,String field){
        // Keep a stable fallback for existing clients and already stored notices.
        try(var locale=Messages.locale("ru_ru")){target.addProperty(field,render());}
        if(parts.stream().noneMatch(p->p.key!=null)){target.remove(field+"I18n");return;}
        var array=new JsonArray();for(var part:parts){var json=new JsonObject();json.addProperty(part.key==null?"text":"key",part.key==null?part.literal:part.key);array.add(json);}
        target.add(field+"I18n",array);
    }
    public static String render(JsonObject source,String field){
        String fallback=Json.opt(source,field,"");
        if(!source.has(field+"I18n")||!source.get(field+"I18n").isJsonArray())return fallback;
        var array=source.getAsJsonArray(field+"I18n");if(array.size()>32)return fallback;
        var result=new StringBuilder();
        for(var value:array){
            if(!value.isJsonObject())return fallback;
            var part=value.getAsJsonObject();
            if(part.has("key")&&part.get("key").isJsonPrimitive()){
                String key=part.get("key").getAsString();if(!key.startsWith("rivet."))return fallback;
                String text=Messages.text(key);if(text.equals(key))return fallback;result.append(text);
            }else if(part.has("text")&&part.get("text").isJsonPrimitive())result.append(part.get("text").getAsString());
            else return fallback;
            if(result.length()>16000)return fallback;
        }
        return result.toString();
    }
    /** Copy only changed containers; ordinary payloads and all literal values remain untouched. */
    public static JsonElement localize(JsonElement value){return localize(value,0);}
    private static JsonElement localize(JsonElement value,int depth){
        if(value==null||value.isJsonNull()||value.isJsonPrimitive()||depth>32)return value;
        if(value.isJsonArray()){
            var source=value.getAsJsonArray();JsonArray result=null;
            for(int i=0;i<source.size();i++){
                var next=localize(source.get(i),depth+1);
                if(next!=source.get(i)){if(result==null){result=new JsonArray(source.size());source.forEach(result::add);}result.set(i,next);}
            }
            return result==null?source:result;
        }
        var source=value.getAsJsonObject();JsonObject result=null;
        for(var entry:source.entrySet()){
            String key=entry.getKey();
            if(key.endsWith("I18n")){
                String field=key.substring(0,key.length()-4);
                if(source.has(field)&&source.get(field).isJsonPrimitive()&&source.get(field).getAsJsonPrimitive().isString()){
                    String translated=render(source,field);
                    if(!translated.equals(source.get(field).getAsString())){if(result==null)result=copy(source);result.addProperty(field,translated);}
                }
            }else{
                var next=localize(entry.getValue(),depth+1);
                if(next!=entry.getValue()){if(result==null)result=copy(source);result.add(key,next);}
            }
        }
        return result==null?source:result;
    }
    private static JsonObject copy(JsonObject source){var result=new JsonObject();source.entrySet().forEach(e->result.add(e.getKey(),e.getValue()));return result;}
}
