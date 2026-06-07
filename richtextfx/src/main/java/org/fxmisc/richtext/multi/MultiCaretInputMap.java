package org.fxmisc.richtext.multi;

import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.wellbehaved.event.InputHandler.Result;
import org.fxmisc.wellbehaved.event.InputMap;

import java.util.function.Predicate;

import static org.fxmisc.wellbehaved.event.EventPattern.dragDetected;
import static org.fxmisc.wellbehaved.event.EventPattern.keyPressed;
import static org.fxmisc.wellbehaved.event.EventPattern.keyTyped;
import static org.fxmisc.wellbehaved.event.EventPattern.mouseDragged;
import static org.fxmisc.wellbehaved.event.EventPattern.mousePressed;
import static org.fxmisc.wellbehaved.event.EventPattern.mouseReleased;
import static org.fxmisc.wellbehaved.event.InputMap.consume;
import static org.fxmisc.wellbehaved.event.InputMap.consumeWhen;
import static org.fxmisc.wellbehaved.event.InputMap.process;
import static org.fxmisc.wellbehaved.event.InputMap.sequence;
import static org.fxmisc.wellbehaved.event.InputMap.when;

/**
 * Builds the layered {@link InputMap} that adds multi-caret gestures and editing on top of a
 * {@link GenericStyledArea}. It is installed with higher priority than the area's built-in behavior (which
 * is registered as a fallback), so these bindings intercept first; everything not bound here falls through
 * to the normal single-caret behavior. Editing/movement bindings are gated on
 * {@link MultiCaretManager#hasExtras()} so the layer is transparent when only the primary caret exists.
 */
final class MultiCaretInputMap {

    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");
    /** Word-wise navigation modifier: Option on macOS, Control elsewhere. */
    private static final KeyCombination.Modifier WORD = MAC ? KeyCombination.ALT_DOWN : KeyCombination.SHORTCUT_DOWN;

    private MultiCaretInputMap() {}

    static <PS, SEG, S> InputMap<?> create(GenericStyledArea<PS, SEG, S> area,
                                           MultiCaretManager<PS, SEG, S> mgr,
                                           BoxSelectionManager<PS, SEG, S> box) {

        // --- Always-on: gestures that create carets / start box select / collapse ----------------------
        InputMap<Event> gestures = sequence(
                // Cmd/Ctrl+Click adds a caret at the clicked position.
                consume(mousePressed(MouseButton.PRIMARY)
                                .onlyIf(e -> e.isShortcutDown() && !e.isShiftDown() && !e.isAltDown()),
                        (MouseEvent e) -> mgr.addCaretAt(area.hit(e.getX(), e.getY()).getInsertionIndex())),

                // Alt+drag: column/box selection.
                consume(mousePressed(MouseButton.PRIMARY).onlyIf(MouseEvent::isAltDown), box::press),
                consume(dragDetected().onlyIf(MouseEvent::isAltDown), (MouseEvent e) -> { /* drag handled below */ }),
                consume(mouseDragged().onlyIf(MouseEvent::isAltDown), box::drag),
                consume(mouseReleased().onlyIf(MouseEvent::isAltDown), box::release),

                // Cmd/Ctrl+D: add next occurrence of the selection.
                consume(keyPressed(KeyCode.D, KeyCombination.SHORTCUT_DOWN), e -> mgr.addNextOccurrence()),

                // Alt+Cmd/Ctrl+Up/Down: add a caret on the line above/below.
                consume(keyPressed(KeyCode.UP, KeyCombination.ALT_DOWN, KeyCombination.SHORTCUT_DOWN),
                        e -> mgr.addCaretLineUpDown(false)),
                consume(keyPressed(KeyCode.DOWN, KeyCombination.ALT_DOWN, KeyCombination.SHORTCUT_DOWN),
                        e -> mgr.addCaretLineUpDown(true)),

                // Escape collapses back to a single caret (only when extras exist).
                consumeWhen(keyPressed(KeyCode.ESCAPE), mgr::hasExtras, e -> mgr.collapseToPrimary()),

                // A plain primary press collapses extras, then lets the default behavior place the caret.
                process(mousePressed(MouseButton.PRIMARY)
                                .onlyIf(e -> !e.isShortcutDown() && !e.isShiftDown() && !e.isAltDown()),
                        (MouseEvent e) -> {
                            if (mgr.hasExtras()) {
                                mgr.collapseToPrimary();
                            }
                            return Result.PROCEED;
                        })
        );

        // --- Multi-caret mode only: editing + movement fanned out to every caret -----------------------
        Predicate<KeyEvent> printable = e -> area.isEditable()
                && e.getCharacter() != null
                && !e.getCharacter().isEmpty()
                && e.getCharacter().charAt(0) >= 0x20
                && e.getCharacter().charAt(0) != 0x7F
                && !e.isControlDown() && !e.isMetaDown();

        InputMap<KeyEvent> editing = sequence(
                consume(keyTyped().onlyIf(printable), e -> mgr.typeText(e.getCharacter())),
                consume(keyPressed(KeyCode.ENTER), e -> mgr.typeText("\n")),
                consume(keyPressed(KeyCode.TAB), e -> mgr.typeText("\t")),

                consume(keyPressed(KeyCode.BACK_SPACE), e -> mgr.deletePrevChar()),
                consume(keyPressed(KeyCode.DELETE), e -> mgr.deleteNextChar()),
                consume(keyPressed(KeyCode.BACK_SPACE, WORD), e -> mgr.deletePrevWord()),
                consume(keyPressed(KeyCode.DELETE, WORD), e -> mgr.deleteNextWord()),

                consume(keyPressed(KeyCode.V, KeyCombination.SHORTCUT_DOWN), e -> mgr.paste()),
                consume(keyPressed(KeyCode.C, KeyCombination.SHORTCUT_DOWN), e -> mgr.copy()),
                consume(keyPressed(KeyCode.X, KeyCombination.SHORTCUT_DOWN), e -> mgr.cut()),
                consume(keyPressed(KeyCode.A, KeyCombination.SHORTCUT_DOWN), e -> {
                    mgr.collapseToPrimary();
                    area.selectAll();
                }),

                horizontal(area, mgr, KeyCode.LEFT, -1),
                horizontal(area, mgr, KeyCode.RIGHT, +1),
                vertical(mgr, KeyCode.UP, false),
                vertical(mgr, KeyCode.DOWN, true),
                lineEdge(mgr, KeyCode.HOME, false),
                lineEdge(mgr, KeyCode.END, true)
        );

        return sequence(gestures, when(mgr::hasExtras, editing));
    }

    private static <PS, SEG, S> InputMap<KeyEvent> horizontal(GenericStyledArea<PS, SEG, S> area,
                                                              MultiCaretManager<PS, SEG, S> mgr,
                                                              KeyCode code, int dir) {
        boolean end = dir > 0;
        InputMap<KeyEvent> base = sequence(
                consume(keyPressed(code), e -> mgr.moveHorizontal(dir, false, false)),
                consume(keyPressed(code, KeyCombination.SHIFT_DOWN), e -> mgr.moveHorizontal(dir, false, true)),
                consume(keyPressed(code, WORD), e -> mgr.moveHorizontal(dir, true, false)),
                consume(keyPressed(code, WORD, KeyCombination.SHIFT_DOWN), e -> mgr.moveHorizontal(dir, true, true))
        );
        if (!MAC) {
            return base;
        }
        // On macOS, Cmd+Left/Right move to line start/end (Option already used for word above).
        return sequence(base,
                consume(keyPressed(code, KeyCombination.SHORTCUT_DOWN), e -> mgr.moveLineBoundary(end, false)),
                consume(keyPressed(code, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                        e -> mgr.moveLineBoundary(end, true)));
    }

    private static <PS, SEG, S> InputMap<KeyEvent> vertical(MultiCaretManager<PS, SEG, S> mgr,
                                                            KeyCode code, boolean down) {
        return sequence(
                consume(keyPressed(code), e -> mgr.moveVertical(down, false)),
                consume(keyPressed(code, KeyCombination.SHIFT_DOWN), e -> mgr.moveVertical(down, true))
        );
    }

    private static <PS, SEG, S> InputMap<KeyEvent> lineEdge(MultiCaretManager<PS, SEG, S> mgr,
                                                            KeyCode code, boolean end) {
        return sequence(
                consume(keyPressed(code), e -> mgr.moveLineBoundary(end, false)),
                consume(keyPressed(code, KeyCombination.SHIFT_DOWN), e -> mgr.moveLineBoundary(end, true))
        );
    }
}
