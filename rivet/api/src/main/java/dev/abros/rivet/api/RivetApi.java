package dev.abros.rivet.api;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Server API v1. Status reads use immutable cached data; domain operations run asynchronously.
 * Instances are thread-safe and scoped to one running server. Retained instances fail with
 * UNAVAILABLE after shutdown; obtain a new connection on the next server start.
 * Java mods in the same JVM are trusted code, not isolated external API clients.
 */
public interface RivetApi {
    int MAJOR_VERSION = 1;
    /** Owner-scoped integration providers; trusted Java consumers only. */
    default IntegrationApi integrations(){throw new ApiException(ApiException.Code.UNAVAILABLE,"Integrations unavailable");}
    /** A new owner lease. Keep it and close it when this integration disables. */
    default MapLayersApi mapLayers(){throw new ApiException(ApiException.Code.MODULE_DISABLED,"Map layers unavailable");}
    default TerrainApi terrain(){throw new ApiException(ApiException.Code.MODULE_DISABLED,"Server terrain disabled");}

    /** Public resources only. No player identity and no player writes. */
    default DomainApi publicDomains(){return domains(null);}

    /** Trusted in-process player context; never grant untrusted callers control of this UUID. */
    default DomainApi domains(java.util.UUID player){
        throw new ApiException(ApiException.Code.MODULE_DISABLED,"Domain API unavailable");
    }

    /** Connect using the installed consumer mod ID. Empty before startup or after shutdown. */
    static Optional<RivetApi> connect(String modId) {
        if (modId == null || !modId.matches("[a-z][a-z0-9_]{1,63}"))
            throw new ApiException(ApiException.Code.INVALID_ARGUMENT, "Invalid consumer mod ID");
        var providers = ServiceLoader.load(RivetApiProvider.class, RivetApi.class.getClassLoader()).iterator();
        if (!providers.hasNext()) return Optional.empty();
        var provider = providers.next();
        if (providers.hasNext()) throw new IllegalStateException("Multiple Rivet API runtimes installed");
        return provider.connect(modId);
    }

    /** Consistent server status and cursor. Status is refreshed at most once per server tick. */
    StatusSnapshot serverStatus();

    /**
     * Read changes strictly after cursor, with a limit of 1..256.
     * RESYNC_REQUIRED means the stream changed or retained history no longer covers the cursor.
     * Take a fresh snapshot in that case. No consumer-owned subscription resources are allocated.
     */
    StatusEvents serverStatusEvents(ApiCursor after, int limit);
}
