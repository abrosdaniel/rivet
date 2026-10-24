package dev.abros.rivet.core;
import java.util.*;
/** Stable LuckPerms nodes and their presentation; does not replace contextual authorization. */
public final class PermissionCatalog {
 public record Entry(String node,String category,boolean adminDefault){public String key(){return "permissions."+node.substring(6);}}
 public static final List<Entry> ENTRIES=List.of(
  new Entry("rivet.admin","administration",false),new Entry("rivet.permissions.manage","administration",false),
  new Entry("rivet.reports","moderation",true),new Entry("rivet.vote.protected","moderation",false),
  new Entry("rivet.events","events",true),new Entry("rivet.map.teleport","map",false),new Entry("rivet.map.seeHiddenPlayers","map",true),
  new Entry("rivet.auth.reset","accounts",false),new Entry("rivet.stats.view","players",true),new Entry("rivet.stats.edit","players",true),
  new Entry("rivet.announce","server",true),new Entry("rivet.maintenance","server",true),new Entry("rivet.restart","server",true),new Entry("rivet.diagnostics","server",true),new Entry("rivet.pack","pack",true));
 private static final List<String> NODES=ENTRIES.stream().map(Entry::node).toList();
 public static List<String> nodes(){return NODES;}
 public static Entry require(String node){return ENTRIES.stream().filter(e->e.node().equals(node)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown Rivet permission"));}
 private PermissionCatalog(){}
}
