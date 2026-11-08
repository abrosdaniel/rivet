package dev.abros.rivet.core;
/** Plain-text channel syntax shared by server routing and tests. */
public final class ChatChannels {
    private ChatChannels() {}
    public static final String LOCAL_COLOR="#83C6C4", GLOBAL_COLOR="#E2BE75", GROUP_COLOR="#B9A3F2", PERSONAL_COLOR="#78C98D";
    public static int color(String value){
        if(value==null||!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.channel_color_expected_rrggbb_bae898a3"));
        return Integer.parseInt(value.substring(1),16);
    }
    public enum Channel { GLOBAL, LOCAL, GROUP, PERSONAL }
    public record Message(Channel channel,String text,String group) {}
    /** Builds a draft only when its syntax routes to the selected audience without truncation. */
    public static java.util.Optional<String> draft(String input,boolean localEnabled,Channel target,String group,int limit){
        return draft(input,localEnabled,target,group,limit,"!","#");
    }
    public static java.util.Optional<String> draft(String input,boolean localEnabled,Channel target,String group,int limit,String globalTrigger,String groupTrigger){
        return draft(input,localEnabled,target,group,limit,globalTrigger,groupTrigger,"");
    }
    public static java.util.Optional<String> draft(String input,boolean localEnabled,Channel target,String group,int limit,String globalTrigger,String groupTrigger,String localTrigger){
        return draft(input,localEnabled,target,group,limit,globalTrigger,groupTrigger,localTrigger,"@");
    }
    public static java.util.Optional<String> draft(String input,boolean localEnabled,Channel target,String group,int limit,String globalTrigger,String groupTrigger,String localTrigger,String personalTrigger){
        if(input.stripLeading().startsWith("/"))return java.util.Optional.empty();
        String body=parse(input,localEnabled,globalTrigger,groupTrigger,localTrigger,personalTrigger).text();
        if(target==Channel.LOCAL&&!localEnabled)return java.util.Optional.empty();
        if((target==Channel.GROUP||target==Channel.PERSONAL)&&(group==null||group.isBlank()||group.contains(":")||group.codePoints().anyMatch(Character::isISOControl)))return java.util.Optional.empty();
        String value=switch(target){case GLOBAL->globalTrigger.isEmpty()?body:globalTrigger+" "+body;case LOCAL->localTrigger.isEmpty()?body:localTrigger+" "+body;case GROUP->groupTrigger+group.strip()+": "+body;case PERSONAL->personalTrigger+group.strip()+": "+body;};
        var routed=parse(value.stripLeading(),localEnabled,globalTrigger,groupTrigger,localTrigger,personalTrigger);
        if(value.stripLeading().startsWith("/")||routed.channel()!=target||!routed.text().equals(body)||(target==Channel.GROUP||target==Channel.PERSONAL)&&!routed.group().equals(group.strip()))return java.util.Optional.empty();
        return value.length()>limit?java.util.Optional.empty():java.util.Optional.of(value);
    }
    public static Message parse(String input,boolean localEnabled) {
        return parse(input,localEnabled,"!","#");
    }
    public static Message parse(String input,boolean localEnabled,String globalTrigger,String groupTrigger) {
        return parse(input,localEnabled,globalTrigger,groupTrigger,"");
    }
    public static Message parse(String input,boolean localEnabled,String globalTrigger,String groupTrigger,String localTrigger) {
        return parse(input,localEnabled,globalTrigger,groupTrigger,localTrigger,"@");
    }
    public static Message parse(String input,boolean localEnabled,String globalTrigger,String groupTrigger,String localTrigger,String personalTrigger) {
        if(!personalTrigger.isEmpty()&&input.startsWith(personalTrigger))return addressed(Channel.PERSONAL,input.substring(personalTrigger.length()).strip());
        if(!localTrigger.isEmpty()&&input.startsWith(localTrigger))return new Message(Channel.LOCAL,input.substring(localTrigger.length()).strip(),"");
        if(!globalTrigger.isEmpty()&&input.startsWith(globalTrigger))return new Message(Channel.GLOBAL,input.substring(globalTrigger.length()).strip(),"");
        if(!groupTrigger.isEmpty()&&input.startsWith(groupTrigger)) {
            String body=input.substring(groupTrigger.length()).strip();int colon=body.indexOf(':');
            return colon<0?new Message(Channel.GROUP,body,""):new Message(Channel.GROUP,body.substring(colon+1).strip(),body.substring(0,colon).strip());
        }
        if(personalTrigger.isEmpty())return addressed(Channel.PERSONAL,input);
        if(globalTrigger.isEmpty())return new Message(Channel.GLOBAL,input,"");
        if(groupTrigger.isEmpty()){int colon=input.indexOf(':');return colon<0?new Message(Channel.GROUP,input,""):new Message(Channel.GROUP,input.substring(colon+1).strip(),input.substring(0,colon).strip());}
        return new Message(localEnabled?Channel.LOCAL:Channel.GLOBAL,input,"");
    }
    private static Message addressed(Channel channel,String input){int colon=input.indexOf(':');return colon<0?new Message(channel,input,""):new Message(channel,input.substring(colon+1).strip(),input.substring(0,colon).strip());}
}
