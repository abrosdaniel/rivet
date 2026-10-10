package dev.abros.rivet.compat.voice;

import dev.abros.rivet.core.VoiceMutes;
import java.util.UUID;
import java.util.function.Supplier;

/** No optional API types cross this boundary; safe to load when voicechat is absent. */
public final class SimpleVoiceBridge {
 public record ClientState(boolean disconnected,boolean muted,boolean disabled){}
 public static volatile boolean registered,serverReady;
 public static volatile Supplier<ClientState> client;
 public static volatile VoiceMutes mutes;
 public static boolean muted(UUID player){var current=mutes;return current!=null&&current.muted(player,System.currentTimeMillis());}
 private SimpleVoiceBridge(){}
}
