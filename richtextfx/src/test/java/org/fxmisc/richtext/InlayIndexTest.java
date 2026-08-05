package org.fxmisc.richtext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The doc/flow index arithmetic inlay decorations rest on. Toolkit-free on purpose: an off-by-one here
 * shows up as a caret landing one character out, which is far cheaper to catch as arithmetic than as a
 * misplaced caret in a running editor.
 */
public class InlayIndexTest {

    private static InlayIndex at(int... cols) {
        return InlayIndex.of(cols);
    }

    /** Round-trips every document index in [0, len] and asserts toDoc undoes toFlow. */
    private static void assertRoundTrips(InlayIndex idx, int len) {
        for (int d = 0; d <= len; d++) {
            assertEquals(d, idx.toDoc(idx.toFlow(d)), "doc " + d + " through " + idx);
        }
    }

    @Test
    public void emptyIsTheIdentityAndIsShared() {
        assertSame(InlayIndex.EMPTY, InlayIndex.of(null));
        assertSame(InlayIndex.EMPTY, InlayIndex.of(new int[0]));
        assertTrue(InlayIndex.EMPTY.isEmpty());
        for (int i = 0; i < 5; i++) {
            assertEquals(i, InlayIndex.EMPTY.toFlow(i));
            assertEquals(i, InlayIndex.EMPTY.toDoc(i));
        }
    }

    @Test
    public void anInlayShiftsEveryIndexAtOrAfterItsColumn() {
        // "copy(true);" with a hint before 'true' at column 5.
        InlayIndex idx = at(5);
        assertEquals(0, idx.toFlow(0));
        assertEquals(4, idx.toFlow(4));
        assertEquals(6, idx.toFlow(5)); // the character at 5 moved past the decoration
        assertEquals(7, idx.toFlow(6));
    }

    @Test
    public void flowIndexOnTheDecorationResolvesToItsColumn() {
        InlayIndex idx = at(5);
        assertEquals(4, idx.toDoc(4));
        assertEquals(5, idx.toDoc(5)); // the decoration itself: nearest real position is its column
        assertEquals(5, idx.toDoc(6));
        assertEquals(6, idx.toDoc(7));
    }

    @Test
    public void severalInlaysAccumulate() {
        InlayIndex idx = at(3, 10, 10, 20);
        assertEquals(2, idx.toFlow(2)); // before every inlay: untouched
        assertEquals(4, idx.toFlow(3)); // at the first inlay's column: shifted past it
        assertEquals(10, idx.toFlow(9));
        assertEquals(13, idx.toFlow(10)); // one earlier inlay + the two sharing column 10
        assertEquals(24, idx.toFlow(20)); // ...plus the one at 20
    }

    @Test
    public void twoInlaysAtOneColumnEachCostAnIndex() {
        InlayIndex idx = at(4, 4);
        assertEquals(2, idx.size());
        assertEquals(4, idx.flowIndexOf(0));
        assertEquals(5, idx.flowIndexOf(1));
        assertEquals(6, idx.toFlow(4));
        assertEquals(4, idx.toDoc(4));
        assertEquals(4, idx.toDoc(5));
        assertEquals(4, idx.toDoc(6));
    }

    @Test
    public void columnZeroIsShiftedToo() {
        // A type hint can sit at the very start of a line.
        InlayIndex idx = at(0);
        assertEquals(0, idx.flowIndexOf(0));
        assertEquals(1, idx.toFlow(0));
        assertEquals(0, idx.toDoc(0));
        assertEquals(0, idx.toDoc(1));
        assertRoundTrips(idx, 20);
    }

    @Test
    public void everyDocumentIndexRoundTrips() {
        assertRoundTrips(at(0), 30);
        assertRoundTrips(at(7), 30);
        assertRoundTrips(at(0, 0, 1, 9, 9, 30), 30);
        assertRoundTrips(at(30), 30); // at the very end of the paragraph
    }

    @Test
    public void inputIsCopiedAndSorted() {
        int[] given = {9, 2, 5};
        InlayIndex idx = InlayIndex.of(given);
        given[0] = 999; // the caller keeps its own array
        assertEquals(2, idx.columnOf(0));
        assertEquals(5, idx.columnOf(1));
        assertEquals(9, idx.columnOf(2));
        assertArrayEquals(new int[] {9, 2, 5}, new int[] {given[0] == 999 ? 9 : given[0], given[1], given[2]});
    }

    @Test
    public void indicesBeforeEveryInlayAreUntouched() {
        InlayIndex idx = at(50, 60);
        for (int d = 0; d < 50; d++) {
            assertEquals(d, idx.toFlow(d));
            assertEquals(d, idx.toDoc(d));
        }
    }
}
