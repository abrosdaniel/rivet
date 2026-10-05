package dev.abros.rivet.core;
/** Plain-text channel syntax shared by server routing and tests. */
public final class ChatChannels {
    private ChatChannels() {}
    public enum Channel { GLOBAL, LOCAL, GROUP }
    public record Message(Channel channel,String text,String group) {}
    public static Message parse(String input,boolean localEnabled) {
        if(input.startsWith("!"))return new Message(Channel.GLOBAL,input.substring(1).strip(),"");
        if(input.startsWith("#")) {
            String body=input.substring(1).strip();int colon=body.indexOf(':');
            return colon<0?new Message(Channel.GROUP,body,""):new Message(Channel.GROUP,body.substring(colon+1).strip(),body.substring(0,colon).strip());
        }
        return new Message(localEnabled?Channel.LOCAL:Channel.GLOBAL,input,"");
    }
}
