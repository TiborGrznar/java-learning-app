package com.tgrznar.javalearningapp.runner.sandbox.result;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Cleans text that came from the sandbox before it leaves the runner. The text is untrusted: it
 * is produced by the student's program or by the compiler. The result is plain text only; it is
 * the client's job to render it as text and never as HTML.
 */
public final class OutputSanitizer {

    /** Maximum length of a message shown to the student. */
    public static final int MESSAGE_LIMIT = 500;

    private static final int MAX_STACK_LINES = 8;
    // Lines such as "[0.123s][warning][os,thread] ..." written by the JVM itself.
    private static final Pattern JVM_LOG_LINE = Pattern.compile("^\\[\\d+\\.\\d+s]\\[[a-z]+].*$");

    private OutputSanitizer() {
    }

    /**
     * Removes JVM log lines, internal paths and control characters (newline and tab stay), then
     * cuts the text to {@code maxChars} characters.
     */
    public static String message(String raw, int maxChars) {
        List<String> kept = new ArrayList<>();
        for (String line : raw.split("\\R", -1)) {
            if (!JVM_LOG_LINE.matcher(line).matches()) {
                kept.add(line);
            }
        }
        String text = String.join("\n", kept)
                .replace("/work/src/", "")
                .replace("/build/", "")
                .replace("/tmp/", "");
        return truncate(removeUnsafeCharacters(text).strip(), maxChars);
    }

    /**
     * Keeps the exception line(s) and at most a few stack frames of the student's own class.
     * Frames of the driver (Main) and of the JDK are dropped.
     */
    public static String runtimeError(String stderr) {
        List<String> kept = new ArrayList<>();
        int stackLines = 0;
        for (String line : stderr.split("\\R", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("at ")) {
                if (trimmed.startsWith("at Solution.") && stackLines < MAX_STACK_LINES) {
                    kept.add("\t" + trimmed);
                    stackLines++;
                }
            } else if (!trimmed.startsWith("...")) {
                kept.add(line);
            }
        }
        return message(String.join("\n", kept), MESSAGE_LIMIT);
    }

    private static String removeUnsafeCharacters(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            boolean control = type == Character.CONTROL || type == Character.FORMAT;
            if (!control || cp == '\n' || cp == '\t') {
                out.appendCodePoint(cp);
            }
        });
        return out.toString();
    }

    private static String truncate(String text, int maxChars) {
        if (text.codePointCount(0, text.length()) <= maxChars) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxChars)) + "…";
    }
}