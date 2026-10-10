package dev.abros.rivet.client;
import java.util.*;
/** Search descriptors shared by settings screens; keys survive ordering and grouping. */
final class SettingsCatalog {
 record Entry(int section,String id,String name,String aliases){}
 static final List<String> EVENT_KEYS=List.of("invite","apply","application","response","workAssign","workDue","reminder","cancel","reschedule","comment","plusComment","workComment","role","plusRemoveMember","plusEventInvite","plusTaskStatus","spark");
 static final List<String> EVENT_NAMES=Client.labels(()->List.of(Client.text("ui.invitations_8d73703e"),Client.text("ui.applications_be11d717"),Client.text("ui.application_decisions_5b53c35e"),Client.text("ui.responses_06cbeae3"),Client.text("ui.task_assignment_8929be51"),Client.text("ui.deadline_changed_9e396d1e"),Client.text("ui.reminders_6c917365"),Client.text("ui.event_cancellation_c7cd0253"),Client.text("ui.event_rescheduled_0ad917f6"),Client.text("ui.comments_5f090363"),Client.text("ui.entry_comments_6bccb93a"),Client.text("ui.task_comments_a3137dfe"),Client.text("ui.role_changed_1d6f731f"),Client.text("ui.removed_from_group_90bb48b4"),Client.text("ui.event_invitation_91c34fd5"),Client.text("ui.task_status_7316eca9"),Client.text("ui.spark_diagnostics_3189c78d")));
 static final List<String> CATEGORIES=List.of("server","home","groups","events","board","polls","ideas","help");
 static final List<String> CATEGORY_NAMES=Client.labels(()->List.of(Client.text("ui.server_917e0514"),Client.text("ui.personal_tasks_18167972"),Client.text("ui.groups_903e08f2"),Client.text("map.layer.events"),Client.text("ui.notice_board_dd5c5a3b"),Client.text("ui.polls_8bd653c3"),Client.text("ui.suggestions_cc07b307"),Client.text("ui.support_617371c8")));
 private static final List<Entry> ENTRIES=Client.labels(SettingsCatalog::create);
 private static void add(List<Entry> out,int section,String keys,String names,String aliases){var ids=keys.split("\\|");var labels=names.split("\\|");if(ids.length!=labels.length)throw new IllegalStateException("Settings descriptors differ");for(int n=0;n<ids.length;n++)out.add(new Entry(section,ids[n],labels[n],aliases));}
 private static List<Entry> create(){var out=new ArrayList<Entry>();
  add(out,0,"theme|opacity|density|contrast|motion|preset",Client.text("ui.interface_theme_transparent_panels_interface_density_00c42cd7"),"");
  add(out,1,"enabled|opacity|chat|inventory|pause|debug|dimMoving|head|name|prefix|suffix|session|ping|order",Client.text("ui.show_widget_background_opacity_in_chat_fbed15a8"),Client.text("ui.hud_widget_60224061"));
  for(String block:HudSettings.BLOCKS)out.add(new Entry(1,"block:"+block,HudSettings.label(block),Client.text("ui.hud_widget_60224061")));
  add(out,2,"digest|toasts|sound|quiet|quietStart|quietEnd|restart|count",Client.text("ui.missed_activity_summary_on_login_show_0e17317f"),Client.text("ui.notifications_cebf063f"));
  for(int n=0;n<CATEGORIES.size();n++)out.add(new Entry(2,"category:"+CATEGORIES.get(n),Client.text("ui.toasts_f6e9771d")+CATEGORY_NAMES.get(n),Client.text("ui.notifications_cebf063f")));
  out.add(new Entry(2,"eventCategory",Client.text("ui.event_settings_scope_770eec8c"),Client.text("ui.notifications_event_filter_39132ea0")));
  for(int n=0;n<EVENT_KEYS.size();n++)out.add(new Entry(2,"event:"+EVENT_KEYS.get(n),EVENT_NAMES.get(n),Client.text("ui.notifications_events_1711b18f")));
  add(out,3,"policy|grouping|heads|ping|footer|opacity|density|width",Client.text("ui.server_mode_player_grouping_player_heads_3fa772d6"),Client.text("ui.tab_ping_525f11dc"));
  add(out,4,"policy|dedupe|hints|hintOpacity|history",Client.text("ui.server_mode_combine_repeated_system_messages_362b7a9c"),Client.text("ui.chat_481f6921"));
  add(out,5,"enabled|opacity|coordinates",Client.text("ui.destination_pointer_pointer_opacity_pointer_coordinates_55f92338"),Client.text("ui.route_navigation_map_17910a88"));
  add(out,7,"enabled|round|rotate|zoom|opacity|coordinates|biome|time|weather",Client.text("ui.show_minimap_round_shape_rotate_with_a0036310"),Client.text("ui.map_minimap_51e65f72"));
  add(out,7,"blockColors|lighting|terrainDepth|slopes|biomeBlend|biomesVanilla|flowers|redstone|stainedGlass|transparency|shortBlocks|hoverBiome|markerScale|markerMinZoom|layers|radarHostile|radarFriendly|radarIcons|radarNames|radarHeight",Client.text("ui.block_colors_map_lighting_height_shading_84a2792b"),Client.text("ui.map_minimap_radar_light_waypoints_ac4f72fe"));
  add(out,7,"caves|caveTop|caveAuto|caveDepth|caveLegible|caveDelay|caveMiniAuto|caveMiniDelay|caveZoom|caveShowTop|caveDefaultType",Client.text("ui.cave_map_mode_slice_height_world_bc62ccc4"),Client.text("ui.caves_underground_floor_7df02324"));
  add(out,7,"markerImport",Client.text("ui.import_waypoints_15dce4fd"),Client.text("ui.xaero_journeymap_import_waypoints_txt_dat_23e5b544"));
  add(out,7,"positionPrivacy",Client.text("ui.my_position_visibility_b6dd519b"),Client.text("ui.players_hidden_nearby_entire_map_audience_3ae81f1c"));
  add(out,8,"timezone|holograms|homeWidgets",Client.text("ui.time_zone_linked_storage_holograms_home_f5dbebdb"),Client.text("ui.general_time_world_storage_home_f1fdc15d"));
  out.add(new Entry(6,"editor",Client.text("ui.element_layout_e9afb6a0"),Client.text("ui.hud_widget_chat_hints_pointer_navigation_810be10e")));return List.copyOf(out);
 }
 static List<Entry> entries(){return ENTRIES.stream().filter(e->(!e.id().equals("positionPrivacy")||MapPositions.available())).toList();}
 static List<Entry> section(int section){return ENTRIES.stream().filter(e->e.section()==section).toList();}
 static String id(int section,String label){if(section==1&&label.equals(Client.text("map.scale")))label=Client.text("ui.widget_scale_3e7f79aa");final String name=label;return section(section).stream().filter(e->e.name().equals(name)).map(Entry::id).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown setting: "+name));}
 static int row(int section,String id){var entries=section(section);for(int n=0;n<entries.size();n++)if(entries.get(n).id().equals(id))return n;throw new IllegalArgumentException("Unknown setting key: "+id);}
 private SettingsCatalog(){}
}
