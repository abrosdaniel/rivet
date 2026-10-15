package dev.abros.rivet.core.compat;

import dev.abros.rivet.api.IntegrationApi;
import java.util.*;

/** Display-only lookup: an alias must never silently transfer a bank account. */
public final class AccountIdentity {
 public static Optional<IntegrationApi.Player> find(IntegrationApi.Identity identities,UUID id){
  if(id==null)return Optional.empty();
  return identities.byId(id).filter(p->id.equals(p.uuid()));
 }
 private AccountIdentity(){}
}
