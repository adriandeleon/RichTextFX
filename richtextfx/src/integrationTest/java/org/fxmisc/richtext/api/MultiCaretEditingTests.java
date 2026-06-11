package org.fxmisc.richtext.api;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import org.fxmisc.richtext.InlineCssTextAreaAppTest;
import org.fxmisc.richtext.multi.MultiCaretController;
import org.fxmisc.richtext.multi.MultiCaretManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Drives {@link MultiCaretManager} programmatically (no robot) to verify multi-caret editing semantics:
 * one atomic/undoable edit at every caret, position shifting, smart paste, and collapse. These run under
 * the TestFX harness and so require a desktop session.
 */
public class MultiCaretEditingTests extends InlineCssTextAreaAppTest {

    private MultiCaretManager<String, String, String> mgr;

    @Override
    public void start(Stage stage) throws Exception {
        super.start(stage);
        MultiCaretController<String, String, String> controller = MultiCaretController.install(area);
        mgr = controller.getManager();
        area.replaceText("aaa\nbbb\nccc");
    }

    @Test
    public void typing_inserts_at_every_caret() {
        interact(() -> {
            area.moveTo(0);
            mgr.addCaretAt(4); // start of second line ("bbb")
            mgr.typeText("X");
            assertEquals("Xaaa\nXbbb\nccc", area.getText());
        });
    }

    @Test
    public void backspace_deletes_at_every_caret() {
        interact(() -> {
            area.moveTo(3);    // end of "aaa"
            mgr.addCaretAt(7); // end of "bbb"
            mgr.deletePrevChar();
            assertEquals("aa\nbb\nccc", area.getText());
        });
    }

    @Test
    public void delete_removes_next_char_at_every_caret() {
        interact(() -> {
            area.moveTo(0);
            mgr.addCaretAt(4);
            mgr.deleteNextChar();
            assertEquals("aa\nbb\nccc", area.getText());
        });
    }

    @Test
    public void multi_caret_edit_is_a_single_undo_step() {
        interact(() -> {
            String original = area.getText();
            area.moveTo(0);
            mgr.addCaretAt(4);
            mgr.typeText("Z");
            assertEquals("Zaaa\nZbbb\nccc", area.getText());

            area.undo();
            assertEquals(original, area.getText());
        });
    }

    @Test
    public void paste_distributes_one_line_per_caret_when_counts_match() {
        interact(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putString("1\n2");
            Clipboard.getSystemClipboard().setContent(content);

            area.moveTo(0);
            mgr.addCaretAt(4);
            mgr.paste();
            assertEquals("1aaa\n2bbb\nccc", area.getText());
        });
    }

    @Test
    public void paste_inserts_full_text_when_counts_differ() {
        interact(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putString("XY");
            Clipboard.getSystemClipboard().setContent(content);

            area.moveTo(0);
            mgr.addCaretAt(4);
            mgr.paste();
            assertEquals("XYaaa\nXYbbb\nccc", area.getText());
        });
    }

    @Test
    public void coincident_carets_merge() {
        interact(() -> {
            area.moveTo(2);
            mgr.addCaretAt(5);
            assertEquals(1, mgr.extraCount());
            // move both carets to the same spot via an edit that collapses them
            mgr.collapseToPrimary();
            area.moveTo(2);
            mgr.addCaretAt(2); // same as primary -> ignored
            assertEquals(0, mgr.extraCount());
        });
    }

    @Test
    public void escape_collapse_leaves_single_caret() {
        interact(() -> {
            mgr.addCaretAt(4);
            mgr.addCaretAt(8);
            assertTrue(mgr.hasExtras());
            assertEquals(3, area.getCaretSet().size());

            mgr.collapseToPrimary();
            assertFalse(mgr.hasExtras());
            assertEquals(1, area.getCaretSet().size());
        });
    }

    @Test
    public void add_next_occurrence_selects_following_match() {
        interact(() -> {
            area.replaceText("foo foo foo");
            area.selectRange(0, 3);   // select first "foo"
            mgr.addNextOccurrence();  // selection non-empty -> add next match
            assertEquals(1, mgr.extraCount());
        });
    }

    @Test
    public void word_delete_at_every_caret_uses_windowed_boundaries() {
        interact(() -> {
            area.replaceText("alpha beta\ngamma delta");
            area.moveTo(10);   // end of first line ("...beta")
            mgr.addCaretAt(22); // end of second line ("...delta")
            mgr.deletePrevWord();
            assertEquals("alpha \ngamma ", area.getText());
        });
    }

    @Test
    public void word_move_at_every_caret() {
        interact(() -> {
            area.replaceText("alpha beta\ngamma delta");
            area.moveTo(0);
            mgr.addCaretAt(11); // start of second line
            mgr.moveHorizontal(+1, true, false); // word-right at both carets
            // primary: 0 -> end of "alpha" (5); extra: 11 -> end of "gamma" (16)
            assertEquals(5, area.getCaretSelectionBind().getPosition());
        });
    }
}
