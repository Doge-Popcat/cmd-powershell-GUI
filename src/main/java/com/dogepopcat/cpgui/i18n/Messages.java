package com.dogepopcat.cpgui.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The language currently in use, and the listeners that want to know when it
 * changes.
 *
 * <p>Text is looked up through {@link Msg}, which holds both translations next to
 * each other. Keeping them in one enum rather than in property files means a
 * missing translation is a compile error instead of a runtime fallback.</p>
 */
public final class Messages {

    private static final List<Consumer<Language>> LISTENERS = new ArrayList<>();

    private static Language current = systemLanguage();

    private Messages() {
    }

    /**
     * @return the language Windows is set to, so the interface matches the rest
     *         of the desktop on first run
     */
    private static Language systemLanguage() {
        return java.util.Locale.getDefault().getLanguage()
                .equalsIgnoreCase(java.util.Locale.CHINESE.getLanguage())
                ? Language.CHINESE
                : Language.ENGLISH;
    }

    /** @return the language the interface is using. */
    public static Language language() {
        return current;
    }

    /**
     * Switches language and notifies every registered listener.
     *
     * @param language the language to use
     */
    public static void setLanguage(Language language) {
        if (language == null || language == current) {
            return;
        }
        current = language;
        for (Consumer<Language> listener : List.copyOf(LISTENERS)) {
            listener.accept(language);
        }
    }

    /**
     * Registers a listener that is told whenever the language changes.
     *
     * @param listener the receiver
     */
    public static void addListener(Consumer<Language> listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    /** @return the text of a message in the current language. */
    public static String text(Msg message) {
        return message.text();
    }

    /**
     * @param message the message to render
     * @param values  values for its {@code %s} or {@code %d} placeholders
     * @return the formatted text
     */
    public static String format(Msg message, Object... values) {
        return String.format(message.text(), values);
    }
}
