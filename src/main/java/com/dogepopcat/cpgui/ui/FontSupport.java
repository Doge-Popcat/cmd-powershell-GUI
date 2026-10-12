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

    /** Preferred console and editor families, best first. */
    private static final String[] PREFERRED = {
            "Consolas", "Cascadia Mono", "Cascadia Code", "Lucida Console",
            "DejaVu Sans Mono", "Courier New"};

    /** Characters a usable code font must be able to draw. */
    private static final String LATIN_SAMPLE =
            "ABCXYZabcxyz0123456789 /\\|-_[](){}<>:;.,'\"~!@#$%^&*+=?";

    private static String preferredFamily;
    private static List<String> usableFamilies;

    private FontSupport() {
    }

    /**
     * @return the family to use when the user has not chosen one: the first
     *         preferred family that is installed, otherwise any monospaced one
     */
    public static synchronized String preferredFamily() {
        if (preferredFamily != null) {
            return preferredFamily;
        }
        List<String> installed = Arrays.asList(installedFamilies());
        for (String candidate : PREFERRED) {
            for (String family : installed) {
                if (family.equalsIgnoreCase(candidate)) {
                    preferredFamily = family;
                    return preferredFamily;
                }
            }
        }
        for (String family : installed) {
            if (isMonospaced(family) && isUsable(family)) {
                preferredFamily = family;
                return preferredFamily;
            }
        }
        preferredFamily = Font.MONOSPACED;
        return preferredFamily;
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
