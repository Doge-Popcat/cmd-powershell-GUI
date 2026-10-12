package com.dogepopcat.cpgui.i18n;

import java.util.Locale;

/**
 * The languages the interface can be shown in.
 *
 * <p>The application's own text is available in both; output produced by
 * {@code cmd.exe} or PowerShell naturally stays in whatever language Windows is
 * installed in, because it is not ours to translate.</p>
 */
public enum Language {

    ENGLISH("English", Locale.ENGLISH),
    CHINESE("\u4e2d\u6587", Locale.SIMPLIFIED_CHINESE);

    private final String displayName;
    private final Locale locale;

    Language(String displayName, Locale locale) {
        this.displayName = displayName;
        this.locale = locale;
    }

    /** @return the name shown in the language menu, in that language. */
    public String displayName() {
        return displayName;
    }

    /** @return the locale this language corresponds to. */
    public Locale locale() {
        return locale;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
