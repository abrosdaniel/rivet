package dev.abros.rivet.core;
import java.time.*;
import java.time.format.DateTimeFormatter;
/** Server presence timestamp displayed in the client's selected time zone. */
public final class LastActivity {
 private LastActivity(){}
 public static String text(long at,long now,ZoneId zone){
  if(at<=0)return dev.abros.rivet.core.Messages.text("rivet.core.last_seen_a_long_time_ago_b48aa2c9");
  long age=Math.max(0,now-at),minutes=age/60000;
  if(minutes==0)return dev.abros.rivet.core.Messages.text("rivet.core.last_seen_just_now_f2c9d2a7");
  if(minutes<60)return dev.abros.rivet.core.Messages.text("rivet.core.last_seen_2d32b2e9")+minutes+" "+word(minutes,dev.abros.rivet.core.Messages.text("rivet.core.minute_c11650fb"),dev.abros.rivet.core.Messages.text("rivet.core.minutes_caa6356c"),dev.abros.rivet.core.Messages.text("rivet.core.minutes_333ed110"))+dev.abros.rivet.core.Messages.text("rivet.core.ago_5938ef22");
  long hours=minutes/60;if(hours<24)return dev.abros.rivet.core.Messages.text("rivet.core.last_seen_2d32b2e9")+hours+" "+word(hours,dev.abros.rivet.core.Messages.text("rivet.core.hour_1e0e6a4d"),dev.abros.rivet.core.Messages.text("rivet.core.hours_4e4e5817"),dev.abros.rivet.core.Messages.text("rivet.core.hours_217fae81"))+dev.abros.rivet.core.Messages.text("rivet.core.ago_5938ef22");
  if(age>=30L*86400000)return dev.abros.rivet.core.Messages.text("rivet.core.last_seen_a_long_time_ago_b48aa2c9");
  return dev.abros.rivet.core.Messages.text("rivet.core.last_seen_2d32b2e9")+DateTimeFormatter.ofPattern(dev.abros.rivet.core.Messages.text("rivet.core.dd_mm_yyyy_at_hh_mm_16c70253")).format(Instant.ofEpochMilli(at).atZone(zone));
 }
 private static String word(long n,String one,String few,String many){if(!Messages.text("rivet.locale").startsWith("ru"))return n==1?one:many;long v=n%100;if(v>=11&&v<=14)return many;return switch((int)(n%10)){case 1->one;case 2,3,4->few;default->many;};}
}
