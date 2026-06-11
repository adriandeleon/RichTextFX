package org.fxmisc.richtext.multi;

import javafx.geometry.Bounds;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.fxmisc.richtext.CaretNode;
import org.fxmisc.richtext.CaretSelectionBind;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.MultiChangeBuilder;
import org.fxmisc.richtext.SelectionImpl;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Owns the multi-caret editing model layered on top of a {@link GenericStyledArea}: the set of extra
 * carets/selections (beyond the area's primary {@link CaretSelectionBind}), the atomic multi-edit
 * algorithm (one {@link MultiChangeBuilder#commit() commit} per operation, so a single undo undoes an
 * edit at every caret), caret movement fan-out, and merging of coincident carets.
 *
 * <p>The area's primary caret is always treated as one more participant, so operations are correct even
 * with zero extras (in which case they degrade to ordinary single-caret edits).</p>
 *
 * <p>All methods must be called on the JavaFX Application Thread.</p>
 */
public final class MultiCaretManager<PS, SEG, S> {

    /** Half-window (chars) scanned on each side of a caret to find a word boundary, so word
     *  navigation/deletion never materializes the whole document. Larger than any realistic word. */
    private static final int WORD_WINDOW = 256;

    private final GenericStyledArea<PS, SEG, S> area;
    private final List<CaretWithSelection<PS, SEG, S>> extras = new ArrayList<>();
    private int nextId = 0;

    MultiCaretManager(GenericStyledArea<PS, SEG, S> area) {
        this.area = area;
    }

    /** Whether any extra carets exist beyond the primary one (i.e. multi-caret mode is active). */
    public boolean hasExtras() {
        return !extras.isEmpty();
    }

    /** The number of extra carets (not counting the primary caret). */
    public int extraCount() {
        return extras.size();
    }

    /* ****************************************************************** *
     * Participant abstraction                                          *
     * ****************************************************************** */

    private CaretSelectionBind<PS, SEG, S> bind() {
        return area.getCaretSelectionBind();
    }

    /** A unified handle over either the primary caret/selection or an extra {@link CaretWithSelection}. */
    private final class Target {
        final boolean primary;
        final CaretNode node;
        final CaretWithSelection<PS, SEG, S> cws; // null when primary

        Target(CaretNode node, CaretWithSelection<PS, SEG, S> cws, boolean primary) {
            this.node = node;
            this.cws = cws;
            this.primary = primary;
        }

        int caret() { return node.getPosition(); }
        int paragraph() { return node.getParagraphIndex(); }
        int column() { return node.getColumnPosition(); }
        int anchor() { return primary ? bind().getAnchorPosition() : cws.anchor; }
        int selStart() { return Math.min(caret(), anchor()); }
        int selEnd() { return Math.max(caret(), anchor()); }

        void setCaret(int pos) {
            pos = clamp(pos);
            if (primary) {
                bind().selectRange(pos, pos);
            } else {
                cws.caret.moveTo(pos);
                cws.selection.selectRange(pos, pos);
                cws.anchor = pos;
            }
        }

        void select(int anchor, int caret) {
            anchor = clamp(anchor);
            caret = clamp(caret);
            if (primary) {
                bind().selectRangeExpl(anchor, caret);
            } else {
                cws.anchor = anchor;
                cws.caret.moveTo(caret);
                cws.selection.selectRange(Math.min(anchor, caret), Math.max(anchor, caret));
            }
        }
    }

    private List<Target> allTargets() {
        List<Target> list = new ArrayList<>(extras.size() + 1);
        list.add(new Target(bind().getUnderlyingCaret(), null, true));
        for (CaretWithSelection<PS, SEG, S> c : extras) {
            list.add(new Target(c.caret, c, false));
        }
        return list;
    }

    /* ****************************************************************** *
     * Editing                                                          *
     * ****************************************************************** */

    /** A single replacement contributed by one participant, in original document coordinates. */
    private final class Op {
        final Target t;
        int start;
        int end;
        String text;
        boolean real;

        Op(Target t, int start, int end, String text) {
            this.t = t;
            this.start = start;
            this.end = end;
            this.text = text;
            this.real = !(start == end && text.isEmpty());
        }
    }

    /** Replace each participant's selection (or zero-width caret) with {@code text}. Covers typing/Enter/Tab. */
    public void typeText(String text) {
        if (!area.isEditable()) {
            return;
        }
        List<Op> ops = new ArrayList<>();
        for (Target t : allTargets()) {
            ops.add(new Op(t, t.selStart(), t.selEnd(), text));
        }
        commitOps(ops);
    }

    /** Backspace at every caret (delete selection, else the previous char). */
    public void deletePrevChar() {
        if (!area.isEditable()) {
            return;
        }
        List<Op> ops = new ArrayList<>();
        for (Target t : allTargets()) {
            if (t.selStart() != t.selEnd()) {
                ops.add(new Op(t, t.selStart(), t.selEnd(), ""));
            } else {
                int c = t.caret();
                ops.add(new Op(t, Math.max(0, c - 1), c, ""));
            }
        }
        commitOps(ops);
    }

    /** Delete at every caret (delete selection, else the next char). */
    public void deleteNextChar() {
        if (!area.isEditable()) {
            return;
        }
        int len = area.getLength();
        List<Op> ops = new ArrayList<>();
        for (Target t : allTargets()) {
            if (t.selStart() != t.selEnd()) {
                ops.add(new Op(t, t.selStart(), t.selEnd(), ""));
            } else {
                int c = t.caret();
                ops.add(new Op(t, c, Math.min(len, c + 1), ""));
            }
        }
        commitOps(ops);
    }

    /** Delete the previous word at every caret (or the selection, if any). */
    public void deletePrevWord() {
        if (!area.isEditable()) {
            return;
        }
        List<Op> ops = new ArrayList<>();
        for (Target t : allTargets()) {
            if (t.selStart() != t.selEnd()) {
                ops.add(new Op(t, t.selStart(), t.selEnd(), ""));
            } else {
                int c = t.caret();
                ops.add(new Op(t, prevWordBoundary(c), c, ""));
            }
        }
        commitOps(ops);
    }

    /** Delete the next word at every caret (or the selection, if any). */
    public void deleteNextWord() {
        if (!area.isEditable()) {
            return;
        }
        List<Op> ops = new ArrayList<>();
        for (Target t : allTargets()) {
            if (t.selStart() != t.selEnd()) {
                ops.add(new Op(t, t.selStart(), t.selEnd(), ""));
            } else {
                int c = t.caret();
                ops.add(new Op(t, c, nextWordBoundary(c), ""));
            }
        }
        commitOps(ops);
    }

    /** Delete each participant's selection (used by cut). No-op for empty selections. */
    private void deleteSelections() {
        List<Op> ops = new ArrayList<>();
        for (Target t : allTargets()) {
            ops.add(new Op(t, t.selStart(), t.selEnd(), ""));
        }
        commitOps(ops);
    }

    /**
     * Paste the system clipboard at every caret. If the clipboard holds exactly as many lines as there are
     * carets (and there is more than one caret), one line is distributed per caret (VS Code behavior);
     * otherwise the whole string is inserted at every caret.
     */
    public void paste() {
        if (!area.isEditable()) {
            return;
        }
        Clipboard cb = Clipboard.getSystemClipboard();
        if (!cb.hasString()) {
            return;
        }
        String s = cb.getString();
        if (s == null || s.isEmpty()) {
            return;
        }
        List<Target> targets = allTargets();
        // Earliest-first so distributed lines map to carets in document order.
        targets.sort(Comparator.comparingInt(Target::selStart).thenComparingInt(Target::selEnd));

        String[] lines = s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean distribute = lines.length == targets.size() && targets.size() > 1;

        List<Op> ops = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            Target t = targets.get(i);
            String text = distribute ? lines[i] : s;
            ops.add(new Op(t, t.selStart(), t.selEnd(), text));
        }
        commitOps(ops);
    }

    /**
     * Copy every selection to the clipboard, joined by newlines (in document order). When <em>no</em>
     * caret has any selected text (e.g. a zero-width column of carets from a straight-down box drag),
     * each caret's whole line is copied instead — VS Code behavior — deduplicating carets that share
     * a line. Without the fallback such a copy silently wrote nothing.
     */
    public void copy() {
        List<Target> targets = allTargets();
        targets.sort(Comparator.comparingInt(Target::selStart));
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (int i = 0; i < targets.size(); i++) {
            Target t = targets.get(i);
            if (t.selStart() == t.selEnd()) {
                continue;
            }
            if (any) {
                sb.append('\n');
            }
            sb.append(area.getText(t.selStart(), t.selEnd()));
            any = true;
        }
        if (!any) {
            for (int par : distinctParagraphs(targets)) {
                if (any) {
                    sb.append('\n');
                }
                sb.append(area.getText(par));
                any = true;
            }
        }
        if (any) {
            ClipboardContent content = new ClipboardContent();
            content.putString(sb.toString());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    /**
     * Cut: copy every selection, then delete them in one undoable step. When no caret has any selected
     * text, each caret's whole line (including its line break) is cut instead — VS Code behavior —
     * deduplicating carets that share a line.
     */
    public void cut() {
        if (!area.isEditable()) {
            return;
        }
        copy();
        for (Target t : allTargets()) {
            if (t.selStart() != t.selEnd()) {
                deleteSelections();
                return;
            }
        }
        // Zero-width carets everywhere: delete each caret's whole line as one undoable commit. One op
        // per distinct line (the first caret on it); other same-line carets are position-tracked through
        // the deletion and cleaned up by mergeCoincident() in commitOps.
        List<Target> targets = allTargets();
        targets.sort(Comparator.comparingInt(Target::selStart));
        Set<Integer> pending = distinctParagraphs(targets);
        List<Op> ops = new ArrayList<>();
        for (Target t : targets) {
            int par = t.paragraph();
            if (!pending.remove(par)) {
                continue; // another caret on this line already contributed the op
            }
            int start = area.getAbsolutePosition(par, 0);
            int end = Math.min(area.getLength(), start + area.getParagraphLength(par) + 1); // +1 = line break
            ops.add(new Op(t, start, end, ""));
        }
        commitOps(ops);
    }

    /** The targets' paragraph indices, deduplicated, in the iteration (document) order. */
    private Set<Integer> distinctParagraphs(List<Target> targets) {
        Set<Integer> pars = new java.util.LinkedHashSet<>();
        for (Target t : targets) {
            pars.add(t.paragraph());
        }
        return pars;
    }

    /**
     * The heart of multi-caret editing: apply all participants' replacements as one atomic commit, then
     * re-assert each caret's position (mirroring the relative-shift arithmetic) and merge coincident carets.
     */
    private void commitOps(List<Op> ops) {
        ops.sort(Comparator.comparingInt((Op o) -> o.start).thenComparingInt(o -> o.end));

        // Demote overlapping replacements to no-ops so the relative builder only ever sees disjoint ranges;
        // the demoted carets collapse into the merged region and are cleaned up by mergeCoincident().
        int lastEnd = Integer.MIN_VALUE;
        for (Op o : ops) {
            if (o.real && o.start < lastEnd) {
                o.start = lastEnd;
                o.end = lastEnd;
                o.text = "";
                o.real = false;
            }
            lastEnd = Math.max(lastEnd, o.end);
        }

        int realCount = 0;
        for (Op o : ops) {
            if (o.real) {
                realCount++;
            }
        }
        if (realCount > 0) {
            MultiChangeBuilder<PS, SEG, S> b = area.createMultiChange(realCount);
            for (Op o : ops) {
                if (o.real) {
                    b.replaceText(o.start, o.end, o.text); // relative: positions auto-shift
                }
            }
            b.commit();
        }

        // Re-assert caret positions in the post-commit document (pure arithmetic, mirroring the
        // relative builder's position cascading).
        int n = ops.size();
        int[] starts = new int[n];
        int[] ends = new int[n];
        int[] lens = new int[n];
        for (int i = 0; i < n; i++) {
            Op o = ops.get(i);
            starts[i] = o.start;
            ends[i] = o.end;
            lens[i] = o.text.length();
        }
        int[] landing = EditMath.landingPositions(starts, ends, lens);
        for (int i = 0; i < n; i++) {
            ops.get(i).t.setCaret(landing[i]);
        }

        mergeCoincident();
    }

    /* ****************************************************************** *
     * Movement                                                         *
     * ****************************************************************** */

    /**
     * Move every caret horizontally by one char (or word). When {@code shift}, extend each selection from
     * its anchor; otherwise collapse to the moved position (or the selection edge in the move direction).
     */
    public void moveHorizontal(int dir, boolean word, boolean shift) {
        int len = area.getLength();
        for (Target t : allTargets()) {
            int caret = t.caret();
            int n;
            if (word) {
                n = dir < 0 ? prevWordBoundary(caret) : nextWordBoundary(caret);
            } else {
                n = clamp(caret + dir, len);
            }
            if (shift) {
                t.select(t.anchor(), n);
            } else {
                if (!word && t.selStart() != t.selEnd()) {
                    n = dir < 0 ? t.selStart() : t.selEnd();
                }
                t.setCaret(n);
            }
        }
        mergeCoincident();
    }

    /** Move every caret to the start/end of its visual paragraph (line). */
    public void moveLineBoundary(boolean end, boolean shift) {
        for (Target t : allTargets()) {
            int home = t.caret() - t.column();
            int n = end ? home + area.getParagraphLength(t.paragraph()) : home;
            if (shift) {
                t.select(t.anchor(), n);
            } else {
                t.setCaret(n);
            }
        }
        mergeCoincident();
    }

    /**
     * Move every caret one visual line up/down, preserving its x position. Uses on-screen caret geometry, so
     * carets currently scrolled out of view are left where they are (best-effort; documented limitation).
     */
    public void moveVertical(boolean down, boolean shift) {
        for (Target t : allTargets()) {
            Optional<Bounds> screen = area.getCaretBoundsOnScreen(t.node);
            if (!screen.isPresent()) {
                continue;
            }
            Bounds local = area.screenToLocal(screen.get());
            if (local == null) {
                continue;
            }
            double x = local.getMinX();
            double h = Math.max(1, local.getHeight());
            double y = down ? local.getMaxY() + h * 0.5 : local.getMinY() - h * 0.5;
            int n = area.hit(x, y).getInsertionIndex();
            if (shift) {
                t.select(t.anchor(), n);
            } else {
                t.setCaret(n);
            }
        }
        mergeCoincident();
    }

    /* ****************************************************************** *
     * Caret creation / collapse                                        *
     * ****************************************************************** */

    /** Add an extra caret at {@code offset} (no-op if one already sits there). */
    public void addCaretAt(int offset) {
        offset = clamp(offset);
        if (offset == bind().getPosition()) {
            return;
        }
        for (CaretWithSelection<PS, SEG, S> c : extras) {
            if (c.caret.getPosition() == offset) {
                return;
            }
        }
        CaretNode caret = new CaretNode("multi-caret-" + nextId, area, offset);
        SelectionImpl<PS, SEG, S> sel = new SelectionImpl<>("multi-selection-" + nextId, area, offset, offset);
        nextId++;
        area.addCaret(caret);
        area.addSelection(sel);
        extras.add(new CaretWithSelection<>(caret, sel, offset));
    }

    /** Add an extra caret with a selection spanning {@code [start,end]} and the caret at {@code end}. */
    public void addCaretWithSelection(int start, int end) {
        start = clamp(start);
        end = clamp(end);
        CaretNode caret = new CaretNode("multi-caret-" + nextId, area, end);
        SelectionImpl<PS, SEG, S> sel = new SelectionImpl<>("multi-selection-" + nextId, area,
                Math.min(start, end), Math.max(start, end));
        nextId++;
        area.addCaret(caret);
        area.addSelection(sel);
        extras.add(new CaretWithSelection<>(caret, sel, start));
    }

    /** Add a caret one visual line above/below the primary caret (Alt+Cmd+Up/Down). */
    public void addCaretLineUpDown(boolean down) {
        CaretNode primary = bind().getUnderlyingCaret();
        Optional<Bounds> screen = area.getCaretBoundsOnScreen(primary);
        if (!screen.isPresent()) {
            return;
        }
        Bounds local = area.screenToLocal(screen.get());
        if (local == null) {
            return;
        }
        double x = local.getMinX();
        double h = Math.max(1, local.getHeight());
        double y = down ? local.getMaxY() + h * 0.5 : local.getMinY() - h * 0.5;
        addCaretAt(area.hit(x, y).getInsertionIndex());
    }

    /**
     * Cmd/Ctrl+D: select the next occurrence of the current selection. If the primary selection is empty,
     * first select the word at the caret (the conventional first press).
     */
    public void addNextOccurrence() {
        CaretSelectionBind<PS, SEG, S> bind = bind();
        if (bind.getStartPosition() == bind.getEndPosition() && !hasExtras()) {
            int[] word = wordRangeAt(bind.getPosition());
            if (word != null) {
                bind.selectRangeExpl(word[0], word[1]);
            }
            return;
        }
        String term = bind.getSelectedText();
        if (term == null || term.isEmpty()) {
            return;
        }
        String text = area.getText();
        int from = 0;
        for (Target t : allTargets()) {
            from = Math.max(from, t.selEnd());
        }
        int idx = text.indexOf(term, from);
        if (idx < 0) {
            idx = text.indexOf(term); // wrap around
        }
        if (idx < 0) {
            return;
        }
        int end = idx + term.length();
        if (rangeAlreadyTaken(idx, end)) {
            return;
        }
        addCaretWithSelection(idx, end);
        area.showParagraphInViewport(area.offsetToPosition(end,
                org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor());
    }

    /** Remove all extra carets, leaving only the primary caret where it is. */
    public void collapseToPrimary() {
        for (CaretWithSelection<PS, SEG, S> c : extras) {
            area.removeCaret(c.caret);
            area.removeSelection(c.selection);
        }
        extras.clear();
    }

    /**
     * Live box-selection update: make the carets match {@code ranges} (range 0 drives the primary, the rest
     * are extras) by <em>reusing existing carets/selections in place</em> and only adding/removing the count
     * delta. Called on every drag pulse, so it must avoid the create+dispose churn of rebuilding the whole
     * set each time. Coincident (ragged-line) carets are left as-is during the drag and merged on release.
     */
    void updateBoxSelection(List<int[]> ranges) {
        if (ranges.isEmpty()) {
            collapseToPrimary();
            return;
        }
        int[] first = ranges.get(0);
        bind().selectRangeExpl(first[0], first[1]);

        int wanted = ranges.size() - 1;   // number of extras the box needs
        int existing = extras.size();
        int overlap = Math.min(wanted, existing);

        // 1. Reuse the carets we already have — just move them.
        for (int i = 0; i < overlap; i++) {
            int[] r = ranges.get(i + 1);
            CaretWithSelection<PS, SEG, S> c = extras.get(i);
            c.caret.moveTo(r[1]);
            c.selection.selectRange(Math.min(r[0], r[1]), Math.max(r[0], r[1]));
            c.anchor = r[0];
        }
        // 2. Remove the surplus (box shrank).
        for (int i = existing - 1; i >= wanted; i--) {
            CaretWithSelection<PS, SEG, S> c = extras.remove(i);
            area.removeCaret(c.caret);
            area.removeSelection(c.selection);
        }
        // 3. Add the deficit (box grew).
        for (int i = existing; i < wanted; i++) {
            int[] r = ranges.get(i + 1);
            addCaretWithSelection(r[0], r[1]);
        }
    }

    /* ****************************************************************** *
     * Helpers                                                          *
     * ****************************************************************** */

    /**
     * Remove extra carets that coincide (same caret position) with the primary or an earlier extra.
     * Runs after every edit/move, so it's allocation-free on the common (no-duplicate) path: no
     * {@code HashSet}/boxing, just an O(M²) scan over the (small) caret count.
     */
    public void mergeCoincident() {
        if (extras.isEmpty()) {
            return;
        }
        int primary = bind().getPosition();
        for (int i = 0; i < extras.size(); ) {
            int pos = extras.get(i).caret.getPosition();
            boolean dup = pos == primary;
            for (int j = 0; !dup && j < i; j++) {
                if (extras.get(j).caret.getPosition() == pos) {
                    dup = true;
                }
            }
            if (dup) {
                CaretWithSelection<PS, SEG, S> c = extras.remove(i);
                area.removeCaret(c.caret);
                area.removeSelection(c.selection);
            } else {
                i++;
            }
        }
    }

    private boolean rangeAlreadyTaken(int start, int end) {
        if (bind().getStartPosition() == start && bind().getEndPosition() == end) {
            return true;
        }
        for (CaretWithSelection<PS, SEG, S> c : extras) {
            if (c.selection.getStartPosition() == start && c.selection.getEndPosition() == end) {
                return true;
            }
        }
        return false;
    }

    /** Previous word boundary before {@code caret}, scanning only a bounded window (not the whole doc). */
    private int prevWordBoundary(int caret) {
        int from = Math.max(0, caret - WORD_WINDOW);
        if (from == caret) {
            return caret;
        }
        String window = area.getText(from, caret);
        return from + WordBreaks.prevBoundary(window, window.length());
    }

    /** Next word boundary after {@code caret}, scanning only a bounded window (not the whole doc). */
    private int nextWordBoundary(int caret) {
        int to = Math.min(area.getLength(), caret + WORD_WINDOW);
        if (to == caret) {
            return caret;
        }
        String window = area.getText(caret, to);
        return caret + WordBreaks.nextBoundary(window, 0);
    }

    private int[] wordRangeAt(int pos) {
        // Bounded window around the caret rather than the whole document.
        int from = Math.max(0, pos - WORD_WINDOW);
        int to = Math.min(area.getLength(), pos + WORD_WINDOW);
        String text = area.getText(from, to);
        if (text.isEmpty()) {
            return null;
        }
        int rel = Math.min(Math.max(0, pos - from), text.length());
        BreakIterator it = BreakIterator.getWordInstance();
        it.setText(text);
        int end = it.following(rel);
        if (end == BreakIterator.DONE) {
            end = text.length();
        }
        int start = it.previous();
        if (start == BreakIterator.DONE) {
            start = 0;
        }
        return start == end ? null : new int[]{from + start, from + end};
    }

    private int clamp(int pos) {
        return clamp(pos, area.getLength());
    }

    private int clamp(int pos, int len) {
        return Math.max(0, Math.min(pos, len));
    }

    /** Dispose all extra carets/selections (called when the controller is uninstalled). */
    void dispose() {
        collapseToPrimary();
    }
}
