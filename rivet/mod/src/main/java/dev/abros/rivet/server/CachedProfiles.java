package dev.abros.rivet.server;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
/** Local cache lookup only: no network, account creation or guessed offline UUID. */
public interface CachedProfiles { Optional<GameProfile> rivet$cachedProfile(String name); }
