package org.fxmisc.richtext.multi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WordBreaksTest {

    @Test
    public void prev_boundary_stops_at_word_start() {
        String t = "hello world";
        assertEquals(6, WordBreaks.prevBoundary(t, 11)); // from end -> start of "world"
        assertEquals(0, WordBreaks.prevBoundary(t, 5));  // from end of "hello" -> 0
    }

    @Test
    public void next_boundary_stops_at_word_end() {
        String t = "hello world";
        assertEquals(5, WordBreaks.nextBoundary(t, 0));  // start -> end of "hello"
        assertEquals(11, WordBreaks.nextBoundary(t, 6)); // start of "world" -> end
    }

    @Test
    public void boundaries_clamp_to_bounds() {
        String t = "abc";
        assertEquals(0, WordBreaks.prevBoundary(t, 0));
        assertEquals(0, WordBreaks.prevBoundary(t, -5));
        assertEquals(3, WordBreaks.nextBoundary(t, 3));
        assertEquals(3, WordBreaks.nextBoundary(t, 99));
    }

    @Test
    public void empty_text_is_safe() {
        assertEquals(0, WordBreaks.prevBoundary("", 0));
        assertEquals(0, WordBreaks.nextBoundary("", 0));
    }
}
