package com.tgrznar.javalearningapp.learning.coding;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether the output of a student's program equals the expected output of a test case.
 * <p>
 * The rule is deliberately strict and simple, so a student can predict it:
 * <ul>
 *   <li>line endings are unified ({@code \r\n} becomes {@code \n}),</li>
 *   <li>spaces at the end of every line are ignored,</li>
 *   <li>blank lines at the very end of the output are ignored,</li>
 *   <li>everything else is compared exactly: case, spaces inside and at the start of a line, blank
 *       lines in the middle, and numbers character by character (no tolerance for decimals).</li>
 * </ul>
 * Tabs are not trimmed: they are part of the text like any other character.
 */
public final class OutputComparator {

    private OutputComparator() {
    }

    /** @return true when both texts are equal under the rule above; a missing actual output never matches. */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return normalize(expected).equals(normalize(actual));
    }

    static String normalize(String text) {
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        List<String> trimmed = new ArrayList<>(lines.length);
        for (String line : lines) {
            trimmed.add(stripTrailingSpaces(line));
        }
        int end = trimmed.size();
        while (end > 0 && trimmed.get(end - 1).isEmpty()) {
            end--;
        }
        return String.join("\n", trimmed.subList(0, end));
    }

    private static String stripTrailingSpaces(String line) {
        int end = line.length();
        while (end > 0 && line.charAt(end - 1) == ' ') {
            end--;
        }
        return line.substring(0, end);
    }
}
