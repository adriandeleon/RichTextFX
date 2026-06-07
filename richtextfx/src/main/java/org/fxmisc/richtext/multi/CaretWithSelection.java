package org.fxmisc.richtext.multi;

import org.fxmisc.richtext.CaretNode;
import org.fxmisc.richtext.SelectionImpl;

/**
 * An extra caret/selection pair tracked by {@link MultiCaretManager}. The {@link CaretNode} carries the
 * blinking caret and the {@link SelectionImpl} renders the (possibly empty) selected range that belongs to
 * it. {@code anchor} is the fixed end of the selection used while shift-extending; it equals the caret
 * position whenever the selection is empty.
 */
final class CaretWithSelection<PS, SEG, S> {

    final CaretNode caret;
    final SelectionImpl<PS, SEG, S> selection;
    int anchor;

    CaretWithSelection(CaretNode caret, SelectionImpl<PS, SEG, S> selection, int anchor) {
        this.caret = caret;
        this.selection = selection;
        this.anchor = anchor;
    }
}
