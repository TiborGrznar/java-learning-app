package com.tgrznar.javalearningapp.runner.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UstarTarWriterTest {

    private record Parsed(String name, byte[] content) {
    }

    /** Result of parsing: the files and the offset right after the last file's data. */
    private record Archive(List<Parsed> files, int endOffset) {
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** Tiny independent tar reader that also verifies every header checksum. */
    private static Archive parse(byte[] tar) {
        List<Parsed> files = new ArrayList<>();
        int pos = 0;
        while (pos + 512 <= tar.length) {
            byte[] header = Arrays.copyOfRange(tar, pos, pos + 512);
            if (isZero(header)) {
                break;
            }
            int nameEnd = 0;
            while (nameEnd < 100 && header[nameEnd] != 0) {
                nameEnd++;
            }
            String name = new String(header, 0, nameEnd, StandardCharsets.US_ASCII);
            int size = (int) Long.parseLong(new String(header, 124, 11, StandardCharsets.US_ASCII), 8);
            long storedChecksum = Long.parseLong(new String(header, 148, 6, StandardCharsets.US_ASCII), 8);

            byte[] forSum = header.clone();
            Arrays.fill(forSum, 148, 156, (byte) ' ');
            long sum = 0;
            for (byte b : forSum) {
                sum += b & 0xFF;
            }
            assertThat(storedChecksum).as("checksum of %s", name).isEqualTo(sum);
            assertThat(header[156]).as("typeflag").isEqualTo((byte) '0');
            assertThat(new String(header, 257, 5, StandardCharsets.US_ASCII)).isEqualTo("ustar");

            files.add(new Parsed(name, Arrays.copyOfRange(tar, pos + 512, pos + 512 + size)));
            pos += 512 + ((size + 511) / 512) * 512;
        }
        return new Archive(files, pos);
    }

    private static boolean isZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    @Test
    void roundTrip_preservesNamesAndContent() {
        byte[] block512 = new byte[512];
        Arrays.fill(block512, (byte) 'x');
        byte[] block513 = new byte[513];
        Arrays.fill(block513, (byte) 'y');
        List<UstarTarWriter.Entry> entries = List.of(
                new UstarTarWriter.Entry("Solution.java", bytes("class Solution {}")),
                new UstarTarWriter.Entry("cases/1.in", new byte[0]),
                new UstarTarWriter.Entry("cases/2.in", block512),
                new UstarTarWriter.Entry("cases/10.in", block513));

        Archive archive = parse(UstarTarWriter.write(entries, 1_000_000));

        assertThat(archive.files()).hasSize(4);
        for (int i = 0; i < entries.size(); i++) {
            assertThat(archive.files().get(i).name()).isEqualTo(entries.get(i).name());
            assertThat(archive.files().get(i).content()).isEqualTo(entries.get(i).content());
        }
    }

    @Test
    void archive_endsWithTwoZeroBlocksAndIsBlockAligned() {
        byte[] tar = UstarTarWriter.write(
                List.of(new UstarTarWriter.Entry("Solution.java", bytes("abc"))), 1000);

        assertThat(tar.length % 512).isZero();
        Archive archive = parse(tar);
        assertThat(tar.length - archive.endOffset()).isEqualTo(1024);
        assertThat(isZero(Arrays.copyOfRange(tar, archive.endOffset(), tar.length))).isTrue();
    }

    @Test
    void utf8Content_isStoredByteForByte() {
        byte[] content = bytes("// príklad: ľščťžýáíé\nclass Solution {}");

        Archive archive = parse(UstarTarWriter.write(
                List.of(new UstarTarWriter.Entry("Solution.java", content)), 1000));

        assertThat(archive.files().get(0).content()).isEqualTo(content);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../x", "a/../b", "/abs", "a//b", "a/", "./a", ".hidden", "a b", "á.java", "a\\b", "a\u0000b"})
    void invalidNames_areRejected(String name) {
        List<UstarTarWriter.Entry> entries = List.of(new UstarTarWriter.Entry(name, bytes("x")));

        assertThatThrownBy(() -> UstarTarWriter.write(entries, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyName_isRejected() {
        List<UstarTarWriter.Entry> entries = List.of(new UstarTarWriter.Entry("", bytes("x")));

        assertThatThrownBy(() -> UstarTarWriter.write(entries, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameOf100Characters_isAcceptedAndOf101IsRejected() {
        String ok = "a".repeat(100);
        String tooLong = "a".repeat(101);

        assertThat(parse(UstarTarWriter.write(
                List.of(new UstarTarWriter.Entry(ok, bytes("x"))), 1000)).files().get(0).name()).isEqualTo(ok);
        assertThatThrownBy(() -> UstarTarWriter.write(
                List.of(new UstarTarWriter.Entry(tooLong, bytes("x"))), 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateNames_areRejected() {
        List<UstarTarWriter.Entry> entries = List.of(
                new UstarTarWriter.Entry("Solution.java", bytes("a")),
                new UstarTarWriter.Entry("Solution.java", bytes("b")));

        assertThatThrownBy(() -> UstarTarWriter.write(entries, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noEntries_isRejected() {
        assertThatThrownBy(() -> UstarTarWriter.write(List.of(), 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contentOverTheLimit_isRejectedAndExactLimitIsAccepted() {
        List<UstarTarWriter.Entry> exact = List.of(
                new UstarTarWriter.Entry("a.java", new byte[60]),
                new UstarTarWriter.Entry("b.java", new byte[40]));
        List<UstarTarWriter.Entry> over = List.of(
                new UstarTarWriter.Entry("a.java", new byte[60]),
                new UstarTarWriter.Entry("b.java", new byte[41]));

        assertThat(UstarTarWriter.write(exact, 100)).isNotEmpty();
        assertThatThrownBy(() -> UstarTarWriter.write(over, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}