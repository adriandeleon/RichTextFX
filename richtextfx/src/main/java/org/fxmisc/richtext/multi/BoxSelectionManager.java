package org.fxmisc.richtext.multi;

import javafx.geometry.Bounds;
import javafx.scene.input.MouseEvent;
import org.fxmisc.richtext.GenericStyledArea;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implements Alt+drag column/box (rectangular) selection on top of {@link MultiCaretManager}. While the
 * user drags with Alt held, the rectangle's per-visual-line column ranges are mapped to one
 * caret+selection per line via {@link MultiCaretManager#setExtrasFromRanges(List)}. On release the
 * per-line carets simply remain as the active multi-caret set, so typing then replaces each line's range.
 *
 * <p>Hit-testing only addresses laid-out (visible) lines, so a box can only cover visible content; a basic
 * scroll nudge lets the user drag past the viewport edge to extend it. Continuous (animation-timer)
 * autoscroll and RTL/wrapped-line exactness are out of scope for this version.</p>
 */
final class BoxSelectionManager<PS, SEG, S> {

    /** Hard cap on the number of lines a single box selection may cover, to bound work. */
    private static final int MAX_LINES = 5000;

    private final GenericStyledArea<PS, SEG, S> area;
    private final MultiCaretManager<PS, SEG, S> manager;

    private double anchorX;
    private double anchorY;
    private boolean active;

    BoxSelectionManager(GenericStyledArea<PS, SEG, S> area, MultiCaretManager<PS, SEG, S> manager) {
        this.area = area;
        this.manager = manager;
    }

    void press(MouseEvent e) {
        anchorX = e.getX();
        anchorY = e.getY();
        active = true;
        area.requestFocus();
    }

    void drag(MouseEvent e) {
        if (!active) {
            return;
        }
        double x = e.getX();
        double y = e.getY();

        // Basic edge autoscroll: dragging past the viewport scrolls so more lines become hit-testable.
        double lineH = lineHeight();
        if (y < 0) {
            area.scrollYBy(-lineH);
        } else if (y > area.getViewportHeight()) {
            area.scrollYBy(lineH);
        }

        manager.setExtrasFromRanges(computeRanges(x, y, lineH));
    }

    void release(MouseEvent e) {
        active = false;
        manager.mergeCoincident();
    }

    private List<int[]> computeRanges(double curX, double curY, double lineH) {
        double left = Math.min(anchorX, curX);
        double right = Math.max(anchorX, curX);
        double top = Math.min(anchorY, curY);
        double bottom = Math.max(anchorY, curY);

        List<int[]> ranges = new ArrayList<>();
        int prevStart = Integer.MIN_VALUE;
        int prevEnd = Integer.MIN_VALUE;
        int count = 0;
        for (double y = top; y <= bottom + 0.0001 && count < MAX_LINES; y += lineH, count++) {
            int s = area.hit(left, y).getInsertionIndex();
            int en = area.hit(right, y).getInsertionIndex();
            int lo = Math.min(s, en);
            int hi = Math.max(s, en);
            if (lo == prevStart && hi == prevEnd) {
                continue; // same line hit twice (short line / rounding)
            }
            ranges.add(new int[]{lo, hi});
            prevStart = lo;
            prevEnd = hi;
        }
        if (ranges.isEmpty()) {
            int p = area.hit(left, top).getInsertionIndex();
            ranges.add(new int[]{p, p});
        }
        return ranges;
    }

    private double lineHeight() {
        Optional<Bounds> b = area.getCaretBoundsOnScreen(area.getCaretSelectionBind().getUnderlyingCaret());
        if (b.isPresent()) {
            Bounds local = area.screenToLocal(b.get());
            if (local != null && local.getHeight() > 1) {
                return local.getHeight();
            }
        }
        return 16;
    }
}
