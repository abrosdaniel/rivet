package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationTest {
    @Test void persistedNotificationsRenderForEachRecipientWithoutTranslatingPlayerText() {
        var notice=new JsonObject();
        String playerTitle="rivet.core.unknown_action_0f4eef3d — пользовательский текст";
        // Select a real catalog key independently of the UI's active language.
        var text=LocalizedText.parts(LocalizedText.key("rivet.ui.notification_3a42cfeb"),LocalizedText.literal(": "+playerTitle));
        try(var locale=Messages.locale("en_us")){text.put(notice,"title");}
        String fallback=Json.str(notice,"title");
        assertTrue(fallback.startsWith("Уведомление: "));
        try(var locale=Messages.locale("en_us")){
            assertEquals("Notification: "+playerTitle,LocalizedText.render(notice,"title"));
            assertEquals(fallback,Json.str(notice,"title"));
        }
        try(var locale=Messages.locale("ru_ru")){assertEquals(fallback,LocalizedText.render(notice,"title"));}
        notice.remove("titleI18n");
        try(var locale=Messages.locale("en_us")){assertEquals(fallback,LocalizedText.render(notice,"title"));}
    }

    @Test void nestedPayloadLocalizationDoesNotMutateReceiptsOrUserContent() {
        var notice=new JsonObject();LocalizedText.key("rivet.ui.notification_3a42cfeb").put(notice,"title");
        notice.addProperty("body","rivet.ui.notification_3a42cfeb");
        var payload=new JsonObject();var array=new com.google.gson.JsonArray();array.add(notice);payload.add("notices",array);
        try(var locale=Messages.locale("en_us")){
            var translated=LocalizedText.localize(payload).getAsJsonObject();
            assertNotSame(payload,translated);
            assertEquals("Notification",translated.getAsJsonArray("notices").get(0).getAsJsonObject().get("title").getAsString());
            assertEquals("Уведомление",notice.get("title").getAsString());
            assertEquals("rivet.ui.notification_3a42cfeb",translated.getAsJsonArray("notices").get(0).getAsJsonObject().get("body").getAsString());
            assertSame(translated,LocalizedText.localize(translated));
        }
        try(var locale=Messages.locale("ru_ru")){assertSame(payload,LocalizedText.localize(payload));}
    }
    @Test void persistedChangeLabelsAndMissingValuesStayLanguageIndependent() {
        var before=new JsonObject();var after=new JsonObject();after.addProperty("title","My title");
        com.google.gson.JsonArray changes;
        try(var locale=Messages.locale("en_us")){changes=ChangeSummary.betweenLocalized(before,after,java.util.Map.of("title",LocalizedText.key("rivet.ui.notification_3a42cfeb")));}
        var row=changes.get(0).getAsJsonObject();assertFalse(row.has("before"));
        try(var locale=Messages.locale("en_us")){
            assertEquals("Notification",LocalizedText.render(row,"label"));
            assertEquals("Not set",ChangeSummary.text(row.get("before")));
        }
        LocalizedText.literal("Player title").put(row,"label");
        assertFalse(row.has("labelI18n"));assertEquals("Player title",LocalizedText.render(row,"label"));
    }

    @Test void orderedQueuesCaptureEachSubmissionRatherThanTheFirstLanguage() {
        var pending=new java.util.ArrayList<Runnable>();
        var serial=new SerialExecutor(pending::add,4);
        var keyed=new KeyedSerialExecutor<String>(pending::add,4);
        var result=new java.util.ArrayList<String>();
        for(String language:java.util.List.of("en_us","ru_ru"))try(var locale=Messages.locale(language)){
            serial.execute(()->result.add(Messages.text("rivet.locale")));
            keyed.execute("same-player",()->result.add(Messages.text("rivet.locale")));
        }
        pending.forEach(Runnable::run);
        assertEquals(java.util.List.of("en_us","ru_ru","en_us","ru_ru"),result);
    }

    @Test void localizedPoolKeepsFutureCancellationAndWorkerIsolation() throws Exception {
        var pool=new LocalizedExecutor(1,1,0,java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(4),Executors.defaultThreadFactory(),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        try {
            pool.submit(()->{entered.countDown();release.await();return null;});
            assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.Future<String> cancelled;
            java.util.concurrent.Future<String> english;
            try(var locale=Messages.locale("en_us")){
                cancelled=pool.submit(()->Messages.text("rivet.locale"));
                english=pool.submit(()->Messages.text("rivet.locale"));
            }
            assertTrue(cancelled.cancel(false));pool.purge();assertEquals(1,pool.getQueue().size());
            release.countDown();assertEquals("en_us",english.get(2,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("ru_ru",pool.submit(()->Messages.text("rivet.locale")).get(2,java.util.concurrent.TimeUnit.SECONDS));
        } finally {release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS));}
    }
    @Test void requestLocaleIsNestedAndDoesNotLeakToNextRequest() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Runnable english;
            try (var locale = Messages.locale("en_us")) {
                english = Messages.capture(() -> assertEquals("en_us", Messages.text("rivet.locale")));
                try (var russian = Messages.locale("ru_ru")) { assertEquals("ru_ru", Messages.text("rivet.locale")); }
                assertEquals("en_us", Messages.text("rivet.locale"));
            }
            executor.submit(english).get();
            assertEquals("ru_ru", executor.submit(() -> Messages.text("rivet.locale")).get());
            assertThrows(RuntimeException.class, () -> {
                try (var locale = Messages.locale("en_us")) { throw new RuntimeException("expected"); }
            });
            assertEquals("ru_ru", Messages.text("rivet.locale"));
        }
    }

    @Test void englishPresenceUsesEnglishPluralRules() {
        try (var locale = Messages.locale("en_us")) {
            long now = 100_000_000;
            assertEquals("Last seen 1 minute ago", LastActivity.text(now-60_000,now,ZoneOffset.UTC));
            assertEquals("Last seen 21 minutes ago", LastActivity.text(now-21*60_000,now,ZoneOffset.UTC));
            assertEquals("Last seen 21 hours ago", LastActivity.text(now-21*3_600_000,now,ZoneOffset.UTC));
            assertFalse(UiHelp.text("Archive").isEmpty());
            assertFalse(UiHelp.text("Mark all as read").isEmpty());
        }
    }

    @Test void draftIdentityAndLegacyChoicesDoNotDependOnLocale() {
        var preset = new JsonObject();
        String original = DraftIdentity.formTitle("edit","board",preset);
        try (var locale = Messages.locale("en_us")) {
            assertEquals(original,DraftIdentity.formTitle("edit","board",preset));
            assertEquals("Редактировать запись",original);
            assertEquals("Игрок: Player\n",DraftIdentity.playerReport("Player"));
            assertTrue(DraftIdentity.booleanValue("Да"));
            assertTrue(DraftIdentity.booleanValue("Yes"));
            assertTrue(DraftIdentity.booleanValue("true"));
            assertFalse(DraftIdentity.booleanValue("Нет"));
            assertFalse(DraftIdentity.booleanValue("false"));
        }
    }
}
