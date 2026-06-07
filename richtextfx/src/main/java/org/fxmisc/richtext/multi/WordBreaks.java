package org.fxmisc.richtext.multi;

import java.text.BreakIterator;

/**
 * Pure helpers for computing word boundaries within a piece of text. Used by {@link MultiCaretManager}
 * for word-wise caret movement and word delete, so that each caret can compute its own boundary
 * independently of the area's primary-caret-only navigation API.
 */
final class WordBreaks {

    private WordBreaks() {}

    /**
     * The offset of the previous word boundary at or before {@code pos}, or {@code 0} if there is none.
     */
    static int prevBoundary(String text, int pos) {
        if (text.isEmpty() || pos <= 0) {
            return 0;
        }
        pos = Math.min(pos, text.length());
        BreakIterator it = BreakIterator.getWordInstance();
        it.setText(text);
        int b = it.preceding(pos);
        return b == BreakIterator.DONE ? 0 : b;
    }

    /**
     * The offset of the next word boundary strictly after {@code pos}, or {@code text.length()} if there is none.
     */
    static int nextBoundary(String text, int pos) {
        if (text.isEmpty()) {
            return 0;
        }
        if (pos >= text.length()) {
            return text.length();
        }
        pos = Math.max(pos, 0);
        BreakIterator it = BreakIterator.getWordInstance();
        it.setText(text);
        int b = it.following(pos);
        return b == BreakIterator.DONE ? text.length() : b;
    }
}
