package dev.abros.rivet.api;

import java.util.*;

/** Owner-scoped transient map registrations. Close when the consumer integration disables. */
public interface MapLayersApi extends AutoCloseable {
    /** Public access is explicit. Empty player set with publicRead=false denies all readers. */
    record Audience(boolean publicRead,Set<UUID> players) {
        public Audience {players=Set.copyOf(players);}
        public static Audience restricted(Set<UUID> players){return new Audience(false,players);}
        public static Audience publicAccess(){return new Audience(true,Set.of());}
    }
    record LayerSpec(String id,String title,Audience audience) {}
    record ObjectSpec(String id,DomainData.Shape shape,Audience audience) {}
    record LayerView(String id,String owner,String title,long revision) {}
    record ObjectView(String id,long revision,DomainData.Shape shape) {}
    record Page<T>(List<T> items,String nextCursor,long snapshot) {
        public Page {items=List.copyOf(items);Objects.requireNonNull(nextCursor);}
    }
    /** ID is local to the installed mod namespace. Existing registrations cannot be adopted. */
    Layer register(LayerSpec specification);
    /** Closes all layers created through this owner handle. Idempotent. */
    @Override void close();

    interface Layer extends AutoCloseable {
        String id();
        /** expectedRevision=0 creates; updates require the revision returned by put. */
        long put(ObjectSpec object,long expectedRevision);
        void remove(String objectId,long expectedRevision);
        /** Closes this exact registration; a replacement registration remains unaffected. */
        @Override void close();
    }
}
