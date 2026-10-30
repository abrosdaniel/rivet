package dev.abros.rivet.compat;
import java.util.UUID;
/** Persistent binding belongs to the address, never to the current holder of its name. */
public interface EnvelopeAddressOwner {
 UUID rivet$owner();
 void rivet$owner(UUID owner);
 boolean rivet$legacy();
 void rivet$legacy(boolean legacy);
}
