package dev.abros.rivet.core;
/** Plain-text channel syntax shared by server routing and tests. */
public final class ChatChannels {
    private ChatChannels() {}
    public static final String LOCAL_COLOR="#83C6C4", GLOBAL_COLOR="#E2BE75", GROUP_COLOR="#B9A3F2";
    public static int color(String value){
        if(value==null||!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.channel_color_expected_rrggbb_bae898a3"));
        return Integer.parseInt(value.substring(1),16);
    }
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
