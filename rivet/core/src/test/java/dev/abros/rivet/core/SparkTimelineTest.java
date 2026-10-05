package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SparkTimelineTest {
    private JsonObject sample(long at,double tps,double mspt){var row=new JsonObject();row.addProperty("at",at);SparkTimeline.metric(row,"tps",tps);SparkTimeline.metric(row,"mspt",mspt);return row;}
    @Test void alertsRequireSustainedLoadAndHaveCooldown(){var timeline=new SparkTimeline();assertFalse(timeline.add(sample(1000,10,80),1000));assertFalse(timeline.add(sample(30000,10,80),30000));assertTrue(timeline.add(sample(31000,10,80),31000));assertFalse(timeline.add(sample(60000,10,80),60000));assertTrue(timeline.add(sample(331000,10,80),331000));}
    @Test void recoveryResetsSustainedThreshold(){var timeline=new SparkTimeline();timeline.add(sample(1000,10,80),1000);assertFalse(timeline.add(sample(30000,20,10),30000));assertFalse(timeline.add(sample(31000,10,80),31000));assertFalse(timeline.add(sample(60000,10,80),60000));}
    @Test void unknownMeasurementsNeverBecomeZeroOrAlerts(){var row=sample(1000,Double.NaN,Double.POSITIVE_INFINITY);assertFalse(row.has("tps"));assertFalse(row.has("mspt"));assertTrue(Double.isNaN(SparkTimeline.number(row,"mspt")));assertFalse(new SparkTimeline().add(row,1000));}
    @Test void wireHistoryIsBoundedAndIndependent(){var timeline=new SparkTimeline();for(int i=0;i<1000;i++){var row=sample(1000+i*2000L,20,12);row.addProperty("cpuProcess",30.0);row.addProperty("largeUnknownField","x".repeat(500));timeline.add(row,1000+i*2000L);}var data=timeline.snapshot();assertTrue(data.size()<=120);assertTrue(data.toString().length()<16000);assertFalse(data.get(0).getAsJsonObject().has("largeUnknownField"));data.get(0).getAsJsonObject().addProperty("tps",0);assertEquals(20,timeline.snapshot().get(0).getAsJsonObject().get("tps").getAsDouble());timeline.clear();assertTrue(timeline.snapshot().isEmpty());}
    @Test void unknownCommandResponsesDoNotPermitStarting(){assertEquals("idle",SparkTimeline.profilerState("[spark] The profiler isn't running!"));assertEquals("running",SparkTimeline.profilerState("[spark] Profiler is already running!"));assertEquals("unknown",SparkTimeline.profilerState("You do not have permission"));}
    @Test void onlyOfficialReportUrlsAreAccepted(){assertEquals("https://spark.lucko.me/Ab_123",SparkTimeline.reportUrl("View: https://spark.lucko.me/Ab_123").orElseThrow());assertTrue(SparkTimeline.reportUrl("https://evil.example/report").isEmpty());assertTrue(SparkTimeline.reportUrl("https://spark.lucko.me.evil.example/report").isEmpty());}
    @Test void customThresholdsDurationAndCooldownAreApplied(){
        var timeline=new SparkTimeline(new SparkTimeline.AlertSettings(15,80,5,10));
        assertFalse(timeline.add(sample(1000,15,80),1000));
        assertFalse(timeline.add(sample(2000,14,40),2000));
        assertFalse(timeline.add(sample(6999,14,40),6999));
        assertTrue(timeline.add(sample(7000,14,40),7000));
        assertFalse(timeline.add(sample(16999,14,40),16999));
        assertTrue(timeline.add(sample(17000,14,40),17000));
        assertFalse(timeline.add(sample(18000,20,20),18000));
        assertFalse(timeline.add(sample(22000,20,81),22000));
        assertTrue(timeline.add(sample(27000,20,81),27000));
    }
}
