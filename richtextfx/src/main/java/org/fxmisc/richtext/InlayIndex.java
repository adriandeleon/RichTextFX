package org.fxmisc.richtext;

import java.util.Arrays;

/**
 * Translation between a paragraph's <b>document</b> character indices and the <b>flow</b> character
 * indices JavaFX assigns once inlay decorations have been injected into the {@link javafx.scene.text.TextFlow}.
 *
 * <p><b>Why this exists.</b> A {@code TextFlow} indexes characters across all of its managed children, and a
 * child that is not a {@link javafx.scene.text.Text} counts as exactly one character — see
 * {@link TextFlowLayout}, which encodes the same rule. So an injected decoration node cannot both displace
 * the glyphs after it (the entire point) and leave the indices alone: participating in layout <em>is</em>
 * participating in the index. An unmanaged child is index-free but displaces nothing, so it is not an option
 * either.
 *
 * <p>Everything above {@link TextFlowExt} — carets, selections, hit-testing, the document model — speaks in
 * document indices. Everything below it speaks in flow indices. This class is the conversion that lets both
 * stay correct, and it is deliberately a separate, toolkit-free type so the arithmetic can be unit-tested
 * without laying out a scene.
 *
 * <p><b>Placement convention:</b> an inlay at document column {@code c} renders immediately <em>before</em>
 * the character at {@code c}. Several inlays may share a column; each costs one flow index, in the order
 * given.
 *
 * <p>Instances are immutable, and {@link #EMPTY} makes both conversions the identity — the overwhelmingly
 * common case, since inlays are off by default and most paragraphs never carry one. Callers on hot paths
 * (caret movement, selection repaint) should test {@link #isEmpty()} and skip the call entirely.
 */
final class InlayIndex {

    /** No decorations: both conversions are the identity. */
    static final InlayIndex EMPTY = new InlayIndex(new int[0]);

    /** Document columns carrying a decoration, ascending; duplicates allowed (one entry per node). */
    private final int[] cols;

    private InlayIndex(int[] sortedCols) {
        this.cols = sortedCols;
    }

    /**
     * An index over the given document columns. The array is copied and sorted, so the caller may keep and
     * mutate its own; a null or empty array yields {@link #EMPTY}.
     */
    static InlayIndex of(int[] docColumns) {
        if (docColumns == null || docColumns.length == 0) {
            return EMPTY;
        }
        int[] copy = Arrays.copyOf(docColumns, docColumns.length);
        Arrays.sort(copy);
        return new InlayIndex(copy);
    }

    boolean isEmpty() {
        return cols.length == 0;
    }

    /** The number of decorations, which is also the total flow-index cost. */
    int size() {
        return cols.length;
    }

    /** The document column of the {@code i}-th decoration, in ascending order. */
    int columnOf(int i) {
        return cols[i];
    }

    /**
     * The flow index the {@code i}-th decoration node itself occupies — its column shifted by the {@code i}
     * decorations that precede it.
     */
    int flowIndexOf(int i) {
        return cols[i] + i;
    }

    /**
     * The flow index of a document index. A decoration at the same column precedes the character, so it is
     * counted: with one inlay at column 5, document index 5 maps to flow index 6.
     */
    int toFlow(int docIdx) {
        if (cols.length == 0) {
            return docIdx;
        }
        int shift = 0;
        // Small arrays (a handful of hints per line at most), so a linear scan beats a binary search and
        // allocates nothing. Bail out as soon as the columns pass the query.
        for (int col : cols) {
            if (col > docIdx) {
                break;
            }
            shift++;
        }
        return docIdx + shift;
    }

    /**
     * The document index of a flow index. A flow index that lands <em>on</em> a decoration node has no
     * character of its own, so it resolves to that decoration's column — the nearest real position, which
     * keeps a caret or hit-test from appearing to sit inside text that does not exist.
     */
    int toDoc(int flowIdx) {
        if (cols.length == 0) {
            return flowIdx;
        }
        int shift = 0;
        for (int i = 0; i < cols.length; i++) {
            int inlayFlow = cols[i] + i;
            if (inlayFlow > flowIdx) {
                break;
            }
            if (inlayFlow == flowIdx) {
                return cols[i];
            }
            shift++;
        }
        return flowIdx - shift;
    }

    @Override
    public String toString() {
        return "InlayIndex" + Arrays.toString(cols);
    }
}
