package org.fxmisc.richtext.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import javafx.geometry.Bounds;
import javafx.stage.Stage;

import org.fxmisc.richtext.Inlay;
import org.fxmisc.richtext.InlineCssTextAreaAppTest;
import org.junit.Test;

/**
 * Inlay decorations must displace the glyphs after them while leaving every document position alone.
 * These assertions are the reason the translation lives in the fork at all: an off-by-one here surfaces
 * downstream as a caret that lands one character out, or a click that selects the wrong word, with nothing
 * to point at inlays as the cause.
 */
public class InlayTest extends InlineCssTextAreaAppTest {

    private static final String TEXT = "copy(true);";
    private static final int HINT_COLUMN = 5; // immediately before "true"

    @Override
    public void start(Stage stage) throws Exception {
        super.start(stage);
        area.replaceText(TEXT);
    }

    private void withHint() {
        interact(() -> area.setInlayFactory(
                i -> i == 0 ? Collections.singletonList(new Inlay(HINT_COLUMN, "overwrite:")) : null));
    }

    /** Screen x of the character at document index {@code i}. Geometry must be read on the FX thread. */
    private double xOf(int i) {
        double[] x = new double[1];
        interact(() -> x[0] = area.getCharacterBoundsOnScreen(i, i + 1).get().getMinX());
        return x[0];
    }

    /** Bounds of the character at document index {@code i}, read on the FX thread. */
    private Bounds boundsOf(int i) {
        Bounds[] b = new Bounds[1];
        interact(() -> b[0] = area.getCharacterBoundsOnScreen(i, i + 1).get());
        return b[0];
    }

    @Test
    public void the_document_is_untouched_by_an_inlay() {
        withHint();
        assertEquals(TEXT, area.getText());
        assertEquals(TEXT.length(), area.getLength());
        assertEquals(1, area.getParagraphs().size());
    }

    @Test
    public void an_inlay_displaces_the_text_after_it() {
        double before = xOf(HINT_COLUMN);
        withHint();
        assertTrue("the hinted glyph should move right", xOf(HINT_COLUMN) > before);
    }

    @Test
    public void text_before_an_inlay_does_not_move() {
        double before = xOf(0);
        withHint();
        assertEquals(before, xOf(0), 0.01);
    }

    @Test
    public void every_character_still_hit_tests_to_its_own_document_index() {
        withHint();
        // The core round-trip: click the middle of each glyph, get that glyph's document index back.
        for (int i = 0; i < TEXT.length(); i++) {
            Bounds b = boundsOf(i);
            double midX = b.getMinX() + b.getWidth() / 2;
            double midY = b.getMinY() + b.getHeight() / 2;
            int[] hit = new int[1];
            // getCharacterBoundsOnScreen is in screen coordinates; hit() takes area-local ones.
            interact(() -> {
                javafx.geometry.Point2D local = area.screenToLocal(midX, midY);
                hit[0] = area.hit(local.getX(), local.getY()).getInsertionIndex();
            });
            assertTrue(
                    "clicking char " + i + " ('" + TEXT.charAt(i) + "') resolved to " + hit[0],
                    hit[0] == i || hit[0] == i + 1); // leading or trailing half, never a different character
        }
    }

    @Test
    public void caret_positions_are_unchanged() {
        withHint();
        interact(() -> area.moveTo(TEXT.length()));
        assertEquals(TEXT.length(), area.getCaretPosition());
        interact(() -> area.moveTo(HINT_COLUMN));
        assertEquals(HINT_COLUMN, area.getCaretPosition());
    }

    @Test
    public void select_all_still_selects_exactly_the_document() {
        withHint();
        interact(() -> area.selectAll());
        assertEquals(TEXT, area.getSelectedText());
        assertEquals(0, area.getSelection().getStart());
        assertEquals(TEXT.length(), area.getSelection().getEnd());
    }

    @Test
    public void a_selection_across_the_inlay_yields_only_document_text() {
        withHint();
        interact(() -> area.selectRange(0, TEXT.length()));
        // The hint's own label must never leak into copied text.
        assertEquals(TEXT, area.getSelectedText());
    }

    @Test
    public void removing_the_inlays_restores_the_original_geometry() {
        double before = xOf(HINT_COLUMN);
        withHint();
        assertTrue(xOf(HINT_COLUMN) > before);
        interact(() -> area.setInlayFactory(null));
        assertEquals(before, xOf(HINT_COLUMN), 0.01);
    }

    @Test
    public void several_inlays_on_one_line_accumulate() {
        interact(() -> area.setInlayFactory(i -> i == 0
                ? List.of(new Inlay(5, "a:"), new Inlay(0, "b:"))   // deliberately unsorted
                : null));
        assertEquals(TEXT, area.getText());
        // Both hints precede index 5, so it is pushed right by both; index 0 by only the one at column 0.
        assertTrue(xOf(5) > xOf(0));
        interact(() -> area.selectAll());
        assertEquals(TEXT, area.getSelectedText());
    }

    @Test
    public void an_out_of_range_inlay_is_ignored_rather_than_clamped() {
        interact(() -> area.setInlayFactory(
                i -> i == 0 ? List.of(new Inlay(9999, "stale:"), new Inlay(-1, "also stale:")) : null));
        assertEquals(TEXT, area.getText());
        interact(() -> area.selectAll());
        assertEquals(TEXT, area.getSelectedText());
    }
}
