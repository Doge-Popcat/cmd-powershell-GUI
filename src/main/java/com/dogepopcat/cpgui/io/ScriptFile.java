package com.dogepopcat.cpgui.io;

import com.dogepopcat.cpgui.shell.ConsoleCharset;
import com.dogepopcat.cpgui.shell.ShellKind;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Reads and writes script files with an encoding the interpreters accept.
 *
 * <p>Getting this right matters more than it looks. Windows PowerShell reads a
 * {@code .ps1} file as UTF-8 only when it starts with a byte order mark, and
 * otherwise falls back to the machine's ANSI code page; {@code cmd.exe} reads a
 * batch file using the console code page. A file written as UTF-8 without a BOM
 * would therefore turn every non-ASCII character into mojibake when run, which
 * is exactly the bug this class exists to avoid.</p>
 */
public final class ScriptFile {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16_LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] UTF16_BE_BOM = {(byte) 0xFE, (byte) 0xFF};

    private ScriptFile() {
    }

    /**
     * Reads a script, honouring any byte order mark and falling back to the
     * console code page when the bytes are not valid UTF-8.
     *
     * @param file the file to read
     * @return the decoded text
     * @throws IOException if the file cannot be read
     */
    public static String read(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());

        if (startsWith(bytes, UTF8_BOM)) {
            return new String(bytes, UTF8_BOM.length, bytes.length - UTF8_BOM.length,
                    StandardCharsets.UTF_8);
        }
        if (startsWith(bytes, UTF16_LE_BOM)) {
            return new String(bytes, UTF16_LE_BOM.length, bytes.length - UTF16_LE_BOM.length,
                    StandardCharsets.UTF_16LE);
        }
        if (startsWith(bytes, UTF16_BE_BOM)) {
            return new String(bytes, UTF16_BE_BOM.length, bytes.length - UTF16_BE_BOM.length,
                    StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            // Legacy batch files are usually in the console code page.
            return new String(bytes, ConsoleCharset.get());
        }
    }

    /**
     * Thrown when a script contains characters the target encoding cannot
     * represent, so the caller can warn instead of silently writing {@code ?}.
     */
    public static final class UnrepresentableCharactersException extends IOException {

        private static final long serialVersionUID = 1L;

        private final transient Charset charset;
        private final String characters;

        UnrepresentableCharactersException(Charset charset, String characters) {
            super(characters + " cannot be encoded in " + charset.name());
            this.charset = charset;
            this.characters = characters;
        }

        /** @return the encoding that could not hold the text. */
        public Charset charset() {
            return charset;
        }

        /** @return the distinct offending characters, for display. */
        public String characters() {
            return characters;
        }
    }

    /**
     * Writes a script in the encoding its interpreter expects.
     *
     * <p>Line endings are normalised to {@code CRLF}: Swing documents store
     * {@code LF} only, and batch files in particular are parsed line by line by
     * {@code cmd.exe}.</p>
     *
     * @param file    destination
     * @param content the text to write
     * @param kind    interpreter the script targets
     * @throws UnrepresentableCharactersException if the console code page cannot
     *                                            hold every character
     * @throws IOException                        if the file cannot be written
     */
    public static void write(File file, String content, ShellKind kind) throws IOException {
        String text = toCrlf(content);
        if (kind == ShellKind.POWERSHELL) {
            byte[] body = text.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[UTF8_BOM.length + body.length];
            System.arraycopy(UTF8_BOM, 0, payload, 0, UTF8_BOM.length);
            System.arraycopy(body, 0, payload, UTF8_BOM.length, body.length);
            Files.write(file.toPath(), payload);
            return;
        }
        Charset charset = ConsoleCharset.get();
        String missing = unrepresentable(text, charset);
        if (!missing.isEmpty()) {
            throw new UnrepresentableCharactersException(charset, missing);
        }
        Files.write(file.toPath(), text.getBytes(charset));
    }

    /**
     * Writes a batch script even when some characters are not representable,
     * replacing them with {@code ?}. Only call this after telling the user.
     *
     * @param file    destination
     * @param content the text to write
     * @param kind    interpreter the script targets
     * @throws IOException if the file cannot be written
     */
    public static void writeLossy(File file, String content, ShellKind kind) throws IOException {
        String text = toCrlf(content);
        if (kind == ShellKind.POWERSHELL) {
            write(file, content, kind);
            return;
        }
        Files.write(file.toPath(), text.getBytes(ConsoleCharset.get()));
    }

    /**
     * @param text    the text to inspect
     * @param charset the encoding to test against
     * @return the distinct characters that {@code charset} cannot encode
     */
    public static String unrepresentable(String text, Charset charset) {
        CharsetEncoder encoder = charset.newEncoder();
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t') {
                continue;
            }
            if (!encoder.canEncode(c) && missing.indexOf(String.valueOf(c)) < 0) {
                missing.append(c);
            }
        }
        return missing.toString();
    }

    /** Converts any mix of line endings to {@code CRLF}. */
    public static String toCrlf(String content) {
        String text = content == null ? "" : content;
        return text.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n");
    }

    /**
     * Guesses the interpreter a file is meant for from its extension.
     *
     * @param file the file, may be {@code null}
     * @return the interpreter to target
     */
    public static ShellKind shellKindFor(File file) {
        if (file == null) {
            return ShellKind.CMD;
        }
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".ps1") || name.endsWith(".psm1") || name.endsWith(".psd1")
                ? ShellKind.POWERSHELL
                : ShellKind.CMD;
    }

    /** @return a sensible default file name for an unsaved tab. */
    public static String defaultFileName(ShellKind kind) {
        return kind == ShellKind.POWERSHELL ? "script.ps1" : "script.cmd";
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length
                && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    /** @return the charset a script of this kind is written with, for messages. */
    public static Charset writeCharset(ShellKind kind) {
        return kind == ShellKind.POWERSHELL ? StandardCharsets.UTF_8 : ConsoleCharset.get();
    }
}
