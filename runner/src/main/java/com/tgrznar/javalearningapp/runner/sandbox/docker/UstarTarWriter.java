package com.tgrznar.javalearningapp.runner.sandbox.docker;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Minimal writer of tar archives in the ustar format, used to stream the submission into the
 * container on stdin. Only regular files are written.
 *
 * <p>The file names are chosen by the runner, never by the student, but the writer still accepts
 * only a strict whitelist (relative, ASCII, no "..", no empty components, at most 100 characters),
 * so a bug elsewhere cannot produce an archive that escapes the extraction directory.
 */
public final class UstarTarWriter {

    /** One regular file of the archive. */
    public record Entry(String name, byte[] content) {
    }

    private static final int BLOCK = 512;
    private static final int MAX_NAME_LENGTH = 100;
    // Components start with a letter, digit or underscore, so "." and ".." can never match.
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]*(/[A-Za-z0-9_][A-Za-z0-9_.-]*)*");

    private UstarTarWriter() {
    }

    /**
     * @param entries       files to write, at least one, unique names
     * @param maxTotalBytes limit of the summed content sizes
     * @throws IllegalArgumentException for an invalid name, a duplicate name, no entries or too much data
     */
    public static byte[] write(List<Entry> entries, long maxTotalBytes) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("No entries");
        }
        Set<String> seen = new HashSet<>();
        long total = 0;
        for (Entry entry : entries) {
            validateName(entry.name());
            if (!seen.add(entry.name())) {
                throw new IllegalArgumentException("Duplicate entry name");
            }
            total += entry.content().length;
            if (total > maxTotalBytes) {
                throw new IllegalArgumentException("Archive content exceeds the limit");
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Entry entry : entries) {
            byte[] content = entry.content();
            out.writeBytes(header(entry.name(), content.length));
            out.writeBytes(content);
            int padding = (BLOCK - content.length % BLOCK) % BLOCK;
            out.writeBytes(new byte[padding]);
        }
        out.writeBytes(new byte[2 * BLOCK]);   // end of archive: two zero blocks
        return out.toByteArray();
    }

    private static void validateName(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid entry name");
        }
    }

    private static byte[] header(String name, long size) {
        byte[] h = new byte[BLOCK];
        putAscii(h, 0, name);
        putOctal(h, 100, 8, 0644);       // mode
        putOctal(h, 108, 8, 0);          // uid
        putOctal(h, 116, 8, 0);          // gid
        putOctal(h, 124, 12, size);
        putOctal(h, 136, 12, 0);         // mtime fixed at 0: reproducible archives
        for (int i = 148; i < 156; i++) {
            h[i] = ' ';                  // the checksum is computed with this field as spaces
        }
        h[156] = '0';                    // regular file
        putAscii(h, 257, "ustar");       // magic "ustar" + NUL
        putAscii(h, 263, "00");          // version
        putOctal(h, 329, 8, 0);          // devmajor
        putOctal(h, 337, 8, 0);          // devminor

        long sum = 0;
        for (byte b : h) {
            sum += b & 0xFF;
        }
        byte[] checksum = String.format("%06o", sum).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(checksum, 0, h, 148, 6);
        h[154] = 0;
        h[155] = ' ';
        return h;
    }

    private static void putAscii(byte[] buf, int offset, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, buf, offset, bytes.length);
    }

    /** Writes the value as zero-padded octal digits followed by a NUL, filling exactly {@code length} bytes. */
    private static void putOctal(byte[] buf, int offset, int length, long value) {
        String digits = Long.toOctalString(value);
        if (digits.length() > length - 1) {
            throw new IllegalArgumentException("Value does not fit the header field");
        }
        String padded = "0".repeat(length - 1 - digits.length()) + digits;
        putAscii(buf, offset, padded);
        buf[offset + length - 1] = 0;
    }
}