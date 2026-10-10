package dev.abros.rivet.client;
import java.util.function.Supplier;
import net.minecraft.locale.Language;
/** Resolves immutable labels once per language/resource reload, never once per frame. */
final class LocalizedValue<T> {
 private final Supplier<T> factory;private Language language;private T value;
 LocalizedValue(Supplier<T> factory){this.factory=factory;}
 synchronized T get(){var current=Language.getInstance();if(current!=language||value==null){value=factory.get();language=current;}return value;}
}
