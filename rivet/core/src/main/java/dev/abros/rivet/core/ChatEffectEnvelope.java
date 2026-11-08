package dev.abros.rivet.core;

import java.util.Optional;

/** Metadata attached only to a live player-chat body, never to an entire formatted line. */
public record ChatEffectEnvelope(int level,long seed) {
    private static final String PREFIX="rivet:chat-body:";
    public ChatEffectEnvelope { if(level<0||level>255)throw new IllegalArgumentException("Invalid effect level"); }
    public String encode(){return PREFIX+level+":"+seed;}
    public static Optional<ChatEffectEnvelope> read(String value) {
        if(value==null||!value.startsWith(PREFIX)||value.length()>64)return Optional.empty();
        var parts=value.substring(PREFIX.length()).split(":",-1);
        if(parts.length!=2)return Optional.empty();
        try{return Optional.of(new ChatEffectEnvelope(Integer.parseInt(parts[0]),Long.parseLong(parts[1])));}
        catch(IllegalArgumentException invalid){return Optional.empty();}
    }
}
