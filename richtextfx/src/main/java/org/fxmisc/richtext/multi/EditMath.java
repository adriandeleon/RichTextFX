package org.fxmisc.richtext.multi;

/**
 * Pure arithmetic for multi-caret edits. Given a set of disjoint replacements (each replacing
 * {@code [start,end)} with text of a given length), applied as one relative
 * {@link org.fxmisc.richtext.MultiChangeBuilder} commit, this computes where each caret ends up in the
 * post-commit document. Extracted as a pure function so it can be unit-tested without a JavaFX toolkit.
 */
final class EditMath {

    private EditMath() {}

    /**
     * Computes the post-edit caret landing position for each replacement. Inputs must be sorted ascending
     * by {@code start} and be pairwise disjoint ({@code starts[i] >= ends[i-1]}).
     *
     * <p>Each caret lands at the end of its own inserted text, shifted by the net length change of all
     * earlier replacements. This mirrors how a relative {@code MultiChangeBuilder} cascades positions.</p>
     *
     * @param starts   replacement range starts (original coordinates)
     * @param ends     replacement range ends (original coordinates)
     * @param textLens length of the inserted text for each replacement
     * @return the new caret position for each replacement, in the post-commit document
     */
    static int[] landingPositions(int[] starts, int[] ends, int[] textLens) {
        int n = starts.length;
        if (ends.length != n || textLens.length != n) {
            throw new IllegalArgumentException("array lengths differ");
        }
        int[] out = new int[n];
        int delta = 0;
        for (int i = 0; i < n; i++) {
            out[i] = starts[i] + delta + textLens[i];
            delta += textLens[i] - (ends[i] - starts[i]);
        }
        return out;
    }
}
