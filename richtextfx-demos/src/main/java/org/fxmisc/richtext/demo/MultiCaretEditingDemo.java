package org.fxmisc.richtext.demo;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.multi.MultiCaretController;

/**
 * Demonstrates VS Code–style multiple cursors and Alt+drag column/box selection, added to a plain
 * {@link CodeArea} with a single call to {@link MultiCaretController#install}.
 *
 * <ul>
 *     <li><b>Cmd/Ctrl+Click</b> — add a caret</li>
 *     <li><b>Cmd/Ctrl+D</b> — select next occurrence (first press selects the word under the caret)</li>
 *     <li><b>Alt+Cmd/Ctrl+Up/Down</b> — add a caret on the line above/below</li>
 *     <li><b>Alt+drag</b> — column/box selection</li>
 *     <li><b>Esc</b> — collapse back to a single caret</li>
 * </ul>
 * Then type, delete, move (arrows / word / home / end), or paste to edit at every caret at once.
 */
public class MultiCaretEditingDemo extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            sb.append("line ").append(i).append(": the quick brown fox\n");
        }
        CodeArea area = new CodeArea(sb.toString());
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));

        MultiCaretController.install(area);

        Label hint = new Label(
                "Cmd/Ctrl+Click: add caret  |  Cmd/Ctrl+D: next occurrence  |  "
                        + "Alt+Cmd/Ctrl+Up/Down: caret above/below  |  Alt+drag: box select  |  Esc: collapse");
        hint.setWrapText(true);
        hint.setStyle("-fx-padding: 6; -fx-font-size: 11;");

        BorderPane root = new BorderPane(area);
        root.setTop(hint);

        stage.setScene(new Scene(root, 640, 480));
        stage.setTitle("RichTextFX — Multiple Cursors & Box Selection");
        stage.show();
        area.requestFocus();
    }
}
