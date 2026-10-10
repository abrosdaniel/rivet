package dev.abros.rivet.core;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Function;

/** Explicit request locale; values and user-authored text never pass through this catalog. */
public final class Messages {
    private static final ThreadLocal<String> LOCALE = new ThreadLocal<>();
    private static volatile Function<String, String> clientResolver;
    private Messages() {}

    private static final class Catalog {
        static final Map<String,String> RU = load("ru_ru");
        static final Map<String,String> EN = load("en_us");
        static Map<String,String> load(String locale) {
            try (var stream = Messages.class.getResourceAsStream("/rivet/i18n/" + locale + ".json")) {
                if (stream == null) throw new IllegalStateException("Missing Rivet message catalog: " + locale);
                try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    var result = new HashMap<String,String>();
                    JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e -> result.put(e.getKey(),e.getValue().getAsString()));
                    return Map.copyOf(result);
                }
            } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read Rivet message catalog",e); }
        }
    }

    public static String text(String key) {
        String locale = LOCALE.get();
        var resolver = clientResolver;
        if (locale == null && resolver != null) return resolver.apply(key);
        return (locale == null || locale.startsWith("ru") ? Catalog.RU : Catalog.EN).getOrDefault(key,key);
    }

    public static void clientResolver(Function<String,String> resolver) { clientResolver = resolver; }

    public static Scope locale(String language) {
        String before = LOCALE.get();
        LOCALE.set(language == null ? "en_us" : language);
        return () -> { if (before == null) LOCALE.remove(); else LOCALE.set(before); };
    }

    public static Runnable capture(Runnable action) {
        String captured = LOCALE.get();
        return () -> {
            String before = LOCALE.get();
            if (captured == null) LOCALE.remove(); else LOCALE.set(captured);
            try { action.run(); }
            finally { if (before == null) LOCALE.remove(); else LOCALE.set(before); }
        };
    }

    public interface Scope extends AutoCloseable { @Override void close(); }
}
