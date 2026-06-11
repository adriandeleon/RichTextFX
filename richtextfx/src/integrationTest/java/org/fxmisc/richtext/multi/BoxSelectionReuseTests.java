package org.fxmisc.richtext.multi;

import javafx.stage.Stage;
import org.fxmisc.richtext.CaretNode;
import org.fxmisc.richtext.InlineCssTextAreaAppTest;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Verifies the P1 box-selection optimization: a same-count live update reuses the existing
 * {@link CaretNode}/selection instances in place rather than disposing and rebuilding them, and
 * count changes add/remove only the delta. Lives in the {@code multi} package to reach the
 * package-private {@link MultiCaretManager#updateBoxSelection(List)}.
 */
public class BoxSelectionReuseTests extends InlineCssTextAreaAppTest {

    private MultiCaretManager<String, String, String> mgr;

    @Override
    public void start(Stage stage) throws Exception {
        super.start(stage);
        mgr = MultiCaretController.install(area).getManager();
        area.replaceText("aaaaa\nbbbbb\nccccc");
    }

    private static List<int[]> ranges(int[]... rs) {
        return Arrays.asList(rs);
    }

    @Test
    public void same_count_update_reuses_caret_instances() {
        interact(() -> {
            mgr.updateBoxSelection(ranges(new int[]{1, 3}, new int[]{7, 9}, new int[]{13, 15}));
            assertEquals(3, area.getCaretSet().size()); // primary + 2 extras
            Set<CaretNode> before = new HashSet<>(area.getCaretSet());

            // Same number of lines, different columns -> must reuse the same caret objects.
            mgr.updateBoxSelection(ranges(new int[]{2, 4}, new int[]{8, 10}, new int[]{14, 16}));
            Set<CaretNode> after = new HashSet<>(area.getCaretSet());

            assertEquals(3, after.size());
            assertEquals(before, after); // identical instances (CaretNode equality is identity)
        });
    }

    @Test
    public void growing_and_shrinking_changes_only_the_delta() {
        interact(() -> {
            mgr.updateBoxSelection(ranges(new int[]{1, 3}, new int[]{7, 9}));
            assertEquals(2, area.getCaretSet().size()); // primary + 1 extra

            mgr.updateBoxSelection(ranges(new int[]{1, 3}, new int[]{7, 9}, new int[]{13, 15}, new int[]{2, 4}));
            assertEquals(4, area.getCaretSet().size()); // primary + 3 extras

            mgr.updateBoxSelection(ranges(new int[]{1, 3}));
            assertEquals(1, area.getCaretSet().size()); // collapsed to primary only
        });
    }
}
