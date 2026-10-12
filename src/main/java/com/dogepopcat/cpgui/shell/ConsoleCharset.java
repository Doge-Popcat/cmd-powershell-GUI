package com.dogepopcat.cpgui.shell;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which charset the Windows console subsystem uses for its input and
 * output streams.
 *
 * <p>When {@code cmd.exe} or {@code powershell.exe} write to a pipe they encode
 * text with the console's OEM code page, not with the machine's ANSI code page
 * or with UTF-8. On a Chinese Windows installation that is code page 936, so
 * decoding with UTF-8 would turn every path into mojibake. The code page is
 * queried once with {@code chcp} and mapped to the matching Java charset.</p>
 */
public final class ConsoleCharset {

    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    private static volatile Charset cached;

    private ConsoleCharset() {
    }

    /** @return the charset to use when talking to a shell process. */
    public static Charset get() {
        Charset local = cached;
        if (local == null) {
            synchronized (ConsoleCharset.class) {
                local = cached;
                if (local == null) {
                    local = detect();
                    cached = local;
                }
            }
        }
        return local;
    }

    private static Charset detect() {
        int codePage = queryCodePage();
        if (codePage > 0) {
            Charset mapped = map(codePage);
            if (mapped != null) {
                return mapped;
            }
        }
        Charset nativeCharset = charsetOrNull(System.getProperty("native.encoding"));
        if (nativeCharset != null) {
            return nativeCharset;
        }
        return StandardCharsets.UTF_8;
    }

    /** Runs {@code chcp} and extracts the numeric code page from its banner. */
    private static int queryCodePage() {
        Process process = null;
        try {
            process = new ProcessBuilder("cmd.exe", "/c", "chcp")
                    .redirectErrorStream(true)
                    .start();
            byte[] bytes;
            try (InputStream in = process.getInputStream()) {
                bytes = in.readAllBytes();
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                return -1;
            }
            // Latin-1 maps every byte to one char, so the digits survive even
            // when the surrounding banner is in another code page.
            String text = new String(bytes, StandardCharsets.ISO_8859_1);
            Matcher matcher = DIGITS.matcher(text);
            int best = -1;
            while (matcher.find()) {
                try {
                    int value = Integer.parseInt(matcher.group(1));
                    if (value >= 1) {
                        best = value;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore absurdly long digit runs.
                }
            }
            return best;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (Exception e) {
            return -1;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    /** Maps a Windows code page identifier onto a Java charset. */
    private static Charset map(int codePage) {
        String name = switch (codePage) {
            case 437 -> "IBM437";
            case 720 -> "IBM720";
            case 737 -> "IBM737";
            case 775 -> "IBM775";
            case 850 -> "IBM850";
            case 852 -> "IBM852";
            case 855 -> "IBM855";
            case 857 -> "IBM857";
            case 858 -> "IBM00858";
            case 860 -> "IBM860";
            case 861 -> "IBM861";
            case 862 -> "IBM862";
            case 863 -> "IBM863";
            case 864 -> "IBM864";
            case 865 -> "IBM865";
            case 866 -> "IBM866";
            case 869 -> "IBM869";
            case 874 -> "x-windows-874";
            case 932 -> "windows-31j";
            case 936 -> "GBK";
            case 949 -> "MS949";
            case 950 -> "MS950";
            case 1250 -> "windows-1250";
            case 1251 -> "windows-1251";
            case 1252 -> "windows-1252";
            case 1253 -> "windows-1253";
            case 1254 -> "windows-1254";
            case 1255 -> "windows-1255";
            case 1256 -> "windows-1256";
            case 1257 -> "windows-1257";
            case 1258 -> "windows-1258";
            case 1361 -> "Johab";
            case 10000 -> "x-MacRoman";
            case 20866 -> "KOI8-R";
            case 21866 -> "KOI8-U";
            case 28591 -> "ISO-8859-1";
            case 28592 -> "ISO-8859-2";
            case 28595 -> "ISO-8859-5";
            case 28597 -> "ISO-8859-7";
            case 65001 -> "UTF-8";
            default -> null;
        };
        return name == null ? null : charsetOrNull(name);
    }

    private static Charset charsetOrNull(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Charset.forName(name.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
