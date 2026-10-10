package dev.abros.rivet.server;

import dev.abros.rivet.core.DatabaseSettings;
import dev.abros.rivet.core.PgDatabase;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;

/** One startup attempt per server; database failures abort startup with a safe diagnostic. */
final class ServerDatabase {
    private static PgDatabase database;
    private static boolean attempted;
    private static dev.abros.rivet.core.ServerSettings settings;
    static dev.abros.rivet.core.ServerSettings settings(){if(settings==null)throw new IllegalStateException("Rivet server settings not loaded");return settings;}

    static synchronized void liveSettings(dev.abros.rivet.core.ServerSettings value){settings=value;}
    static void install() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
            (net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) -> { reset(); try { settings=dev.abros.rivet.core.ServerSettings.load(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().normalize()); } catch(java.io.IOException error){throw new IllegalStateException("Rivet: cannot read config/rivet-server.toml; startup stopped");} get(); });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
            (net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> reset());
    }

    private static synchronized void reset() {
        if (database != null) database.close();
        database = null;
        attempted = false;
        settings = null;
    }

    static synchronized PgDatabase get() {
        if (!attempted) {
            attempted = true;
            try {
                database = new PgDatabase(settings().database());
            } catch (Exception failure) {
                // Do not print raw driver exceptions or configuration values: they may contain secrets.
                com.mojang.logging.LogUtils.getLogger().error(
                    dev.abros.rivet.core.Messages.text("rivet.core.rivet_postgresql_is_not_configured_or_a4ee2fbe") +
                    dev.abros.rivet.core.Messages.text("rivet.core.check_config_rivet_server_toml_database_dfbc48fa") +
                    dev.abros.rivet.core.Messages.text("rivet.core.host_and_port_availability_tls_and_f6c677b7"));
            }
        }
        if(database==null)throw new IllegalStateException(dev.abros.rivet.core.Messages.text("rivet.core.rivet_server_startup_stopped_postgresql_is_0a9fd3a1"));
        return database;
    }
}
