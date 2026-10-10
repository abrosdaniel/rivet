package dev.abros.rivet.core;
/** Hints describe consequences, never repeat a visible caption. */
public final class UiHelp {
 private UiHelp(){}
 public static String text(String label){
  if(label.startsWith(dev.abros.rivet.core.Messages.text("rivet.core.transparent_panels_78978c3e")))return dev.abros.rivet.core.Messages.text("rivet.core.show_the_game_world_through_rivet_709a95db");
  if(label.startsWith(dev.abros.rivet.core.Messages.text("rivet.core.minecraft_gui_scale_cea04eea")))return dev.abros.rivet.core.Messages.text("rivet.core.changes_the_size_of_text_and_5acf2e21");
  if(label.startsWith(dev.abros.rivet.core.Messages.text("rivet.core.second_layer_46e7d7d3")))return dev.abros.rivet.core.Messages.text("rivet.core.shows_the_outer_layer_only_in_aaa0f721");
  if(label.equals(dev.abros.rivet.core.Messages.text("rivet.ui.classic_arms_87d0a86d"))||label.equals(dev.abros.rivet.core.Messages.text("rivet.ui.slim_arms_894a2812")))return dev.abros.rivet.core.Messages.text("rivet.core.select_the_model_the_png_was_81b6aebb");
  if(label.startsWith(dev.abros.rivet.core.Messages.text("rivet.core.remind_me_1b33785d")))return dev.abros.rivet.core.Messages.text("rivet.core.personal_reminder_before_an_event_does_cd02e7af");

   if(label.equals(dev.abros.rivet.core.Messages.text("rivet.ui.restrict_92a86e44")))return dev.abros.rivet.core.Messages.text("rivet.core.this_player_will_no_longer_be_2851932e");
   if(label.equals(dev.abros.rivet.core.Messages.text("rivet.ui.archive_3789b399")))return dev.abros.rivet.core.Messages.text("rivet.core.completed_and_closed_entries_in_this_17b43d4e");
   if(label.equals(dev.abros.rivet.core.Messages.text("rivet.core.trash_d9e801f1")))return dev.abros.rivet.core.Messages.text("rivet.core.deleted_entries_you_have_permission_to_2ba1f9cc");
   if(label.equals(dev.abros.rivet.core.Messages.text("rivet.ui.remove_location_d37c6be6")))return dev.abros.rivet.core.Messages.text("rivet.core.removes_the_linked_coordinates_takes_effect_d84a851d");
   if(label.equals(dev.abros.rivet.core.Messages.text("rivet.ui.revoke_all_devices_and_sessions_50d8361f")))return dev.abros.rivet.core.Messages.text("rivet.core.ends_active_sessions_you_will_need_52915a16");
   if(label.equals(dev.abros.rivet.core.Messages.text("rivet.ui.cancel_with_reason_613af8a2")))return dev.abros.rivet.core.Messages.text("rivet.core.stops_the_vote_without_applying_a_a4648daa");
   if(label.equals(dev.abros.rivet.core.Messages.text("rivet.ui.transfer_leadership_11d9c3a0")))return dev.abros.rivet.core.Messages.text("rivet.core.another_member_will_become_the_group_bca87e0a");
   if(label.equals(dev.abros.rivet.core.Messages.text("rivet.ui.mark_all_as_read_9ebe990e")))return dev.abros.rivet.core.Messages.text("rivet.core.marks_all_notifications_as_read_including_938d83ff");
   if(label.equals(dev.abros.rivet.core.Messages.text("rivet.ui.hide_entry_26aa9823")))return dev.abros.rivet.core.Messages.text("rivet.core.removes_the_entry_from_the_public_e19a7111");
   return "";

 }
}
