package dev;

import com.dogepopcat.cpgui.ui.Icons;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer tool: renders the application icon to a Windows {@code .ico} file so
 * {@code jpackage} can use it.
 *
 * <p>The artwork stays in {@link Icons} as code; this only converts it at build
 * time, which keeps binary assets out of the repository. Windows accepts PNG
 * payloads inside an ICO container since Vista, so each size is simply an
 * encoded PNG preceded by a directory entry.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.IconExport build/cpgui.ico
 * </pre>
 */
public final class IconExport {

    /** Sizes Windows picks from, from the task bar up to the large tile view. */
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private IconExport() {
    }

    public static void main(String[] args) throws IOException {
        File target = new File(args.length > 0 ? args[0] : "build/cpgui.ico");
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }

        List<byte[]> frames = new ArrayList<>();
        List<Integer> frameSizes = new ArrayList<>();
        for (int size : SIZES) {
            frames.add(toPng(Icons.appIcon(size)));
            frameSizes.add(size);
        }

        try (OutputStream out = Files.newOutputStream(target.toPath());
             DataOutputStream data = new DataOutputStream(out)) {
            // ICONDIR
            writeShortLE(data, 0);
            writeShortLE(data, 1);
            writeShortLE(data, frames.size());

            int offset = 6 + 16 * frames.size();
            for (int i = 0; i < frames.size(); i++) {
                int size = frameSizes.get(i);
                data.writeByte(size >= 256 ? 0 : size);   // 0 means 256
                data.writeByte(size >= 256 ? 0 : size);
                data.writeByte(0);                        // palette size
                data.writeByte(0);                        // reserved
                writeShortLE(data, 1);                    // colour planes
                writeShortLE(data, 32);                   // bits per pixel
                writeIntLE(data, frames.get(i).length);
                writeIntLE(data, offset);
                offset += frames.get(i).length;
            }
            for (byte[] frame : frames) {
                data.write(frame);
            }
        }
        System.out.println("wrote " + target + " (" + target.length() + " bytes, "
                + frames.size() + " sizes)");
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        return buffer.toByteArray();
    }

    private static void writeShortLE(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >>> 8) & 0xFF);
    }

    private static void writeIntLE(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >>> 8) & 0xFF);
        out.writeByte((value >>> 16) & 0xFF);
        out.writeByte((value >>> 24) & 0xFF);
    }
}
