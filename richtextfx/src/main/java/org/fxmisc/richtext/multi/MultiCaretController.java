package org.fxmisc.richtext.multi;

import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;

/**
 * Adds VS Code–style <strong>multiple cursors</strong> and <strong>Alt+drag column/box selection</strong> to
 * any {@link GenericStyledArea} (e.g. {@code CodeArea}, {@code StyleClassedTextArea}, {@code InlineCssTextArea}).
 *
 * <p>This is a non-invasive add-on built entirely on the area's public API
 * ({@link GenericStyledArea#addCaret addCaret}/{@link GenericStyledArea#addSelection addSelection},
 * {@link GenericStyledArea#createMultiChange() createMultiChange}, {@link GenericStyledArea#hit hit}) plus a
 * higher-priority {@link InputMap} layered over the area's built-in (fallback) behavior. Install it once per
 * area:</p>
 *
 * <pre><code>
 * MultiCaretController&lt;?, ?, ?&gt; mc = MultiCaretController.install(codeArea);
 * // ... later, if needed:
 * mc.dispose();
 * </code></pre>
 *
 * <h3>Gestures</h3>
 * <ul>
 *     <li><b>Cmd/Ctrl+Click</b> — add a caret</li>
 *     <li><b>Cmd/Ctrl+D</b> — select the next occurrence of the current selection (first press selects the word)</li>
 *     <li><b>Alt+Cmd/Ctrl+Up/Down</b> — add a caret on the line above/below</li>
 *     <li><b>Alt+drag</b> — column/box selection</li>
 *     <li><b>Esc</b> — collapse back to a single caret</li>
 * </ul>
 *
 * <p>While multiple carets exist, typing, Enter/Tab, Backspace/Delete (and word variants), paste (with VS
 * Code–style one-line-per-caret distribution), copy/cut, and caret movement (arrows, word, home/end, and on
 * macOS Cmd+Left/Right) all apply to every caret, each edit being a single undoable step.</p>
 *
 * <p>Methods on the returned controller and its {@link #getManager() manager} must be called on the JavaFX
 * Application Thread. Known limitations: box selection covers visible lines (with a basic scroll nudge at the
 * viewport edge); IME composition and RTL/wrapped-line box geometry are not specially handled.</p>
 */
public final class MultiCaretController<PS, SEG, S> {

    private final GenericStyledArea<PS, SEG, S> area;
    private final MultiCaretManager<PS, SEG, S> manager;
    private final InputMap<?> inputMap;
    private boolean disposed = false;

    private MultiCaretController(GenericStyledArea<PS, SEG, S> area) {
        this.area = area;
        this.manager = new MultiCaretManager<>(area);
        BoxSelectionManager<PS, SEG, S> box = new BoxSelectionManager<>(area, manager);
        this.inputMap = MultiCaretInputMap.create(area, manager, box);
        Nodes.addInputMap(area, inputMap);
    }

    /** Install multi-caret + box-selection support on {@code area} and return the controlling handle. */
    public static <PS, SEG, S> MultiCaretController<PS, SEG, S> install(GenericStyledArea<PS, SEG, S> area) {
        return new MultiCaretController<>(area);
    }

    /** The manager driving the carets, for programmatic control (adding carets, collapsing, etc.). */
    public MultiCaretManager<PS, SEG, S> getManager() {
        return manager;
    }

    /** Remove the input bindings and all extra carets, restoring the area's default single-caret behavior. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        Nodes.removeInputMap(area, inputMap);
        manager.dispose();
    }
}
