package org.fxmisc.richtext;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import javafx.scene.Node;
import javafx.scene.text.Font;
import javafx.scene.text.TextFlow;

import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyledSegment;
import org.fxmisc.richtext.model.TextOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

/**
 * The preferred height of a {@link ParagraphBox} is served from a cache keyed on the width it is asked for,
 * and the cache is dropped by every change that can alter the answer (each of which reaches the box through
 * {@code requestLayout()}). Flowless asks every visible cell for its height on every edit, so a stale value
 * would corrupt scrolling and a missing cache costs a text layout per visible line per keystroke.
 */
public class ParagraphBoxPrefHeightTest {

    private static final TextOps<String, String> OPS = SegmentOps.styledTextOps();

    private static ParagraphBox<String, String, String> box(String text) {
        Paragraph<String, String, String> par = new Paragraph<>("ps", OPS, text, "s");
        Function<StyledSegment<String, String>, Node> nodeFactory = seg -> new TextExt(seg.getSegment());
        return new ParagraphBox<>(par, (TextFlow flow, String ps) -> {}, nodeFactory);
    }

    @Test
    public void unchangedBoxIsMeasuredOnce() {
        ParagraphBox<String, String, String> b = box("hello world");
        double h = b.prefHeight(800);
        assertTrue(h > 0);
        assertEquals(1, b.prefHeightComputations());

        assertEquals(h, b.prefHeight(800));
        // without wrapping the width does not matter, so a different width is the same key
        assertEquals(h, b.prefHeight(123));
        assertEquals(1, b.prefHeightComputations());
    }

    @Test
    public void explicitRequestLayoutDropsTheCache() {
        ParagraphBox<String, String, String> b = box("hello world");
        b.prefHeight(800);
        b.requestLayout();
        b.prefHeight(800);
        assertEquals(2, b.prefHeightComputations());
    }

    @Test
    public void wrapWidthIsPartOfTheKey() {
        ParagraphBox<String, String, String> b = box("a fairly long line of text that will wrap once the width is small enough to force it");
        b.wrapTextProperty().set(true); // requestLayout -> no cache yet anyway
        double wide = b.prefHeight(2000);
        assertEquals(1, b.prefHeightComputations());
        double narrow = b.prefHeight(60);
        assertEquals(2, b.prefHeightComputations());
        assertTrue(narrow > wide, "narrow width must wrap to more lines");

        // the last key is the narrow one; asking for it again is a cache hit, the wide one a miss
        assertEquals(narrow, b.prefHeight(60));
        assertEquals(2, b.prefHeightComputations());
        assertEquals(wide, b.prefHeight(2000));
        assertEquals(3, b.prefHeightComputations());
    }

    @Test
    public void wrapTextChangeDropsTheCache() {
        ParagraphBox<String, String, String> b = box("hello world");
        b.prefHeight(800);
        b.wrapTextProperty().set(true);
        b.prefHeight(800);
        assertEquals(2, b.prefHeightComputations());
    }

    @Test
    public void fontChangeOnASegmentNodeDropsTheCache() {
        ParagraphBox<String, String, String> b = box("hello world");
        double small = b.prefHeight(800);
        TextExt t = (TextExt) b.lookupAll(".text").stream()
                .filter(n -> n instanceof TextExt).findFirst()
                .orElseGet(() -> firstTextExt(b));
        t.setFont(Font.font(t.getFont().getFamily(), 48));
        double big = b.prefHeight(800);
        assertEquals(2, b.prefHeightComputations());
        assertTrue(big > small, "a bigger font must yield a taller box: " + big + " vs " + small);
    }

    private static TextExt firstTextExt(ParagraphBox<?, ?, ?> b) {
        for (Node child : b.getChildrenUnmodifiable()) {
            if (child instanceof TextFlow) {
                for (Node n : ((TextFlow) child).getChildrenUnmodifiable()) {
                    if (n instanceof TextExt) return (TextExt) n;
                }
            }
        }
        throw new AssertionError("no TextExt in box");
    }

    @Test
    public void gutterGraphicChangeDropsTheCache() {
        ParagraphBox<String, String, String> b = box("hello world");
        b.prefHeight(800);
        b.graphicFactoryProperty().set(i -> new javafx.scene.shape.Rectangle(10, 60));
        b.setIndex(0); // the graphic is resolved from the index; adding it changes the children
        double withGraphic = b.prefHeight(800);
        assertEquals(2, b.prefHeightComputations());
        assertTrue(withGraphic > 0);
    }

    /**
     * Inlay nodes are Labels, and a Control cannot be built before the toolkit runs. This test starts it
     * (which needs a display) and is skipped where that is impossible; the mechanism it checks, a child
     * change under the box, is also covered by the gutter-graphic and font tests above.
     */
    private static void startToolkitOrSkip() {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException alreadyRunning) {
            // fine
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "FX toolkit unavailable: " + t);
        }
        javafx.application.Platform.setImplicitExit(false);
    }

    @Test
    public void inlayChangeDropsTheCache() {
        startToolkitOrSkip();
        ParagraphBox<String, String, String> b = box("hello world");
        b.setIndex(0);
        b.prefHeight(800);
        int before = b.prefHeightComputations();
        b.inlayFactoryProperty().set(i -> List.of(new Inlay(5, ": String")));
        b.prefHeight(800);
        assertEquals(before + 1, b.prefHeightComputations());
        // same inlays again: ParagraphText ignores the no-op, so nothing is recomputed
        b.inlayFactoryProperty().set(i -> List.of(new Inlay(5, ": String")));
        b.prefHeight(800);
        assertEquals(before + 1, b.prefHeightComputations());
        b.inlayFactoryProperty().set(i -> Collections.emptyList());
        b.prefHeight(800);
        assertEquals(before + 2, b.prefHeightComputations());
    }

    @Test
    public void foldedBoxIsZeroWithoutTouchingTheCache() {
        ParagraphBox<String, String, String> b = box("hello world");
        double h = b.prefHeight(800);
        TextFlow text = (TextFlow) b.getChildrenUnmodifiable().get(0);
        text.setVisible(false);
        assertTrue(b.isFolded());
        assertEquals(0.0, b.prefHeight(800));
        text.setVisible(true);
        assertEquals(h, b.prefHeight(800));
    }
}
