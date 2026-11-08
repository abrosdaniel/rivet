package dev.abros.rivet.server;

import dev.abros.rivet.core.CommunityStore;
import dev.abros.rivet.core.PlayerSessions;
import dev.abros.rivet.core.ServerWorkQueues;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;

/** Shared data lifetime. Queued work drains before its stores and moderation data close. */
final class ServerDataRuntime {
    private static volatile ServerWorkQueues queues;
    private static CommunityStore community;
    private static final PlayerSessions<ServerGamePacketListenerImpl> sessions = new PlayerSessions<>();

    static void install() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (PlayerEvent.PlayerLoggedInEvent event) -> {
            if (queues != null && event.getEntity() instanceof ServerPlayer player) {
                sessions.open(player.getUUID(), player.connection);
                ServerClientDelivery.views().remove(player.getUUID());
                ServerChangeDelivery.forget(player.getUUID());
            }
        });
        // Feature cleanup sees the departing session; a late logout cannot close its replacement.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && sessions.close(player.getUUID(), player.connection)) {
                ServerClientDelivery.views().remove(player.getUUID());
                ServerChangeDelivery.forget(player.getUUID());
            }
        });
    }

    static void start() throws Exception {
        if (queues != null || community != null) throw new IllegalStateException("Rivet data already started");
        sessions.clear();
        ServerClientDelivery.views().clear();
        var settings = ServerDatabase.settings();
        community = new CommunityStore(ServerDatabase.get(), settings.modules().sections(settings.community()),
                settings.taskLimits(), settings.modules());
        community.retention(settings.number("retention.reportDays"), settings.number("retention.trashDays"), settings.number("retention.auditEntries"));
        ServerExtras.start(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("rivet"), community);
        queues = new ServerWorkQueues();
    }

    static ServerWorkQueues workers() {
        var current = queues;
        if (current == null) throw new RejectedExecutionException("Rivet data workers are stopped");
        return current;
    }
    static PlayerSessions<ServerGamePacketListenerImpl> sessions() { return sessions; }
    static CommunityStore community() {
        var current = community;
        if (current == null) throw new IllegalStateException("Rivet data is stopped");
        return current;
    }
    static void stop() {
        sessions.clear();
        ServerClientDelivery.views().clear();
        var previous = queues;
        queues = null;
        try {
            if (previous != null && !previous.stop(Duration.ofSeconds(5), Duration.ofSeconds(5)))
                com.mojang.logging.LogUtils.getLogger().warn("Rivet data workers still running after forced shutdown timeout");
        } finally {
            ServerExtras.stop();
            community = null;
        }
    }
    private ServerDataRuntime() {}
}
