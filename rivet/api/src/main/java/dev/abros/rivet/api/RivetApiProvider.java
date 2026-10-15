package dev.abros.rivet.api;

import java.util.Optional;

/** Service discovery implemented by Rivet only. Consumers use RivetApi.connect. */
public interface RivetApiProvider {
    Optional<RivetApi> connect(String modId);
}
