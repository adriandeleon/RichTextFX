package org.fxmisc.richtext.multi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class EditMathTest {

    @Test
    public void single_insert_lands_after_inserted_text() {
        // insert "ab" (len 2) at offset 3
        int[] r = EditMath.landingPositions(new int[]{3}, new int[]{3}, new int[]{2});
        assertArrayEquals(new int[]{5}, r);
    }

    @Test
    public void typing_one_char_at_three_carets_shifts_later_carets() {
        // Carets at 0, 5, 10, each inserts "x" (len 1).
        int[] r = EditMath.landingPositions(
                new int[]{0, 5, 10}, new int[]{0, 5, 10}, new int[]{1, 1, 1});
        // 0->1; 5 shifted by +1 ->6 then +1 ->7; 10 shifted by +2 ->12 then +1 ->13
        assertArrayEquals(new int[]{1, 7, 13}, r);
    }

    @Test
    public void deletions_shift_later_carets_left() {
        // Backspace (delete 1 char) at carets that were at 3 and 8: ranges [2,3) and [7,8), text "".
        int[] r = EditMath.landingPositions(
                new int[]{2, 7}, new int[]{3, 8}, new int[]{0, 0});
        // first deletes -> lands at 2; second start 7 shifted by -1 -> 6, +0 -> 6
        assertArrayEquals(new int[]{2, 6}, r);
    }

    @Test
    public void mixed_replace_of_different_lengths() {
        // Replace [0,2) with "abc" (len3) and [5,7) with "" (delete).
        int[] r = EditMath.landingPositions(
                new int[]{0, 5}, new int[]{2, 7}, new int[]{3, 0});
        // first: 0+0+3 = 3; delta after = 3-2 = +1; second: 5+1+0 = 6
        assertArrayEquals(new int[]{3, 6}, r);
    }

    @Test
    public void no_op_carets_only_shift() {
        // A no-op caret (zero-width, empty text) at 4, plus a real insert "yy" at 0.
        int[] r = EditMath.landingPositions(
                new int[]{0, 4}, new int[]{0, 4}, new int[]{2, 0});
        assertArrayEquals(new int[]{2, 6}, r);
    }
}
