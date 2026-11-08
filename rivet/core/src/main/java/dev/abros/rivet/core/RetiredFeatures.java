package dev.abros.rivet.core;

import java.util.Set;

/** Retired mechanics retain their stored data for read-only export, never runtime activation. */
public final class RetiredFeatures {
    public static final Set<String> IDS=Set.of("tasks","storage","board","events","polls","ideas");
    public static boolean contains(String id){return IDS.contains(id);}
    public static void requireActive(String id){if(contains(id))throw new CommunityFailure(CommunityFailure.Code.UNAVAILABLE,Messages.text("rivet.core.feature.retired"));}
    private RetiredFeatures(){}
}
