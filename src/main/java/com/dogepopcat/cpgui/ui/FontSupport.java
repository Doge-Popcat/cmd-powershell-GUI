package com.dogepopcat.cpgui.ui;

import java.awt.Canvas;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Chooses which installed fonts are worth offering and picks a sensible default.
 *
 * <p>Windows ships a number of symbol and pi fonts whose Latin code points map to
 * pictures rather than letters. Selecting one turns every command into boxes or
 * blank space, so they are filtered out. The default is Consolas, which is what
 * a modern Windows console uses.</p>
 */
public final class FontSupport {

    /** Families whose letters are really symbols, so they must not be offered. */
    private static final Set<String> SYMBOL_FAMILIES = Set.of(
            "wingdings", "wingdings 2", "wingdings 3", "webdings", "symbol", "marlett",
            "bookshelf symbol 7", "monotype sorts", "mt extra", "ms reference specialty",
            "ms reference sans serif", "ms outlook", "signs", "jester", "hollywood",
            "webdings 2", "cambria math", "segoe ui symbol", "segoe ui emoji",
            "segoe mdl2 assets", "segoe mdl2 assets 2", "segoe fluent icons");

    /** Preferred console and editor families for Latin text, best first. */
    private static final String[] PREFERRED = {
            "Consolas", "Cascadia Mono", "Cascadia Code", "Lucida Console",
            "DejaVu Sans Mono", "Courier New"};

    /**
     * Families that also draw Chinese, used when the Latin favourites cannot.
     * New SimSun is what a Windows console uses on a Chinese installation.
     */
    private static final String[] PREFERRED_CJK = {
            "NSimSun", "SimSun", "Microsoft YaHei Mono", "MingLiU", "MS Gothic", "SimHei"};

    /** Characters a usable code font must be able to draw. */
    private static final String LATIN_SAMPLE =
            "ABCXYZabcxyz0123456789 /\\|-_[](){}<>:;.,'\"~!@#$%^&*+=?";

    /** Chinese characters the default font should also be able to draw. */
    private static final String CJK_SAMPLE = "\u4e2d\u6587\u547d\u4ee4\u6807\u7b7e\u9875";

    private static String preferredFamily;
    private static List<String> usableFamilies;

    private FontSupport() {
    }

    /**
     * @return the family to use when the user has not chosen one. A family that
     *         can draw both Latin and Chinese is preferred, because a script may
     *         contain either; Consolas is used when it qualifies, and New SimSun,
     *         the Chinese Windows console font, when it does not
     */
    public static synchronized String preferredFamily() {
        if (preferredFamily != null) {
            return preferredFamily;
        }
        // Windows reports family names in the system language, so matching has to
        // consider the English names as well: "NSimSun" is listed as "新宋体" on a
        // Chinese installation, and Font accepts either spelling.
        List<String> candidates = allFamilyNames();
        preferredFamily = firstInstalled(candidates, PREFERRED, true);
        if (preferredFamily == null) {
            preferredFamily = firstInstalled(candidates, PREFERRED_CJK, true);
        }
        if (preferredFamily == null) {
            for (String family : installedFamilies()) {
                if (isLogicalFamily(family)) {
                    continue;
                }
                if (isMonospaced(family) && isUsable(family) && supportsCjk(family)) {
                    preferredFamily = family;
                    break;
                }
            }
        }
        if (preferredFamily == null) {
            // The logical font always has a Chinese fallback behind it.
            preferredFamily = Font.MONOSPACED;
        }
        return preferredFamily;
    }

    /** @return the five logical families, which are not physical installations. */
    private static boolean isLogicalFamily(String family) {
        return family.equals(Font.DIALOG) || family.equals(Font.DIALOG_INPUT)
                || family.equals(Font.MONOSPACED) || family.equals(Font.SANS_SERIF)
                || family.equals(Font.SERIF);
    }

    /** @return every installed family name, in both the system and English form. */
    private static List<String> allFamilyNames() {
        List<String> names = new ArrayList<>();
        for (String name : installedFamilies()) {
            names.add(name);
        }
        try {
            for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames(Locale.ENGLISH)) {
                names.add(name);
            }
        } catch (RuntimeException ignored) {
            // The English list is a convenience; the local one is enough.
        }
        return names;
    }

    /** @return the first candidate that is installed, optionally CJK capable. */
    private static String firstInstalled(List<String> installed, String[] candidates,
                                         boolean requireCjk) {
        for (String candidate : candidates) {
            for (String family : installed) {
                if (family.equalsIgnoreCase(candidate)
                        && (!requireCjk || supportsCjk(candidate))) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * @param family the font family to test
     * @return {@code true} when it can draw Chinese characters
     */
    public static boolean supportsCjk(String family) {
        if (family == null || family.isBlank()) {
            return false;
        }
        try {
            return new Font(family, Font.PLAIN, 12).canDisplayUpTo(CJK_SAMPLE) < 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** @return a font in the preferred family at the given size. */
    public static Font preferredFont(int size) {
        return new Font(preferredFamily(), Font.PLAIN, size);
    }

    /**
     * @param family the font family to test
     * @return {@code true} when the family draws letters rather than symbols
     */
    public static boolean isUsable(String family) {
        if (family == null || family.isBlank()) {
            return false;
        }
        if (SYMBOL_FAMILIES.contains(family.toLowerCase(Locale.ROOT))) {
            return false;
        }
        try {
            Font font = new Font(family, Font.PLAIN, 12);
            return font.canDisplayUpTo(LATIN_SAMPLE) < 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * @return every installed family that can draw text, monospaced ones first
     *         and each group in alphabetical order
     */
    public static synchronized List<String> usableFamilies() {
        if (usableFamilies != null) {
            return usableFamilies;
        }
        String[] installed = installedFamilies();
        Arrays.sort(installed, String.CASE_INSENSITIVE_ORDER);
        List<String> monospaced = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String family : installed) {
            if (!isUsable(family)) {
                continue;
            }
            if (isMonospaced(family)) {
                monospaced.add(family);
            } else {
                others.add(family);
            }
        }
        List<String> ordered = new ArrayList<>(monospaced);
        ordered.addAll(others);
        usableFamilies = List.copyOf(ordered);
        return usableFamilies;
    }

    /**
     * @param family the font family to test
     * @return {@code true} when every glyph is the same width
     */
    public static boolean isMonospaced(String family) {
        try {
            Font font = new Font(family, Font.PLAIN, 12);
            FontMetrics metrics = new Canvas().getFontMetrics(font);
            return metrics.charWidth('i') > 0 && metrics.charWidth('i') == metrics.charWidth('W');
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** @return every font family the platform reports. */
    private static String[] installedFamilies() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
        } catch (RuntimeException e) {
            return new String[]{Font.MONOSPACED};
        }
    }
}
