package org.fxmisc.richtext;

import java.util.Objects;

/**
 * A decoration rendered <b>inline</b> at a document column, occupying layout width without being part of
 * the document — an editor's "inlay hint" (a parameter name before an argument, an inferred type after a
 * declaration).
 *
 * <p>An inlay is <em>not</em> text. It never appears in the paragraph's content, never shifts a document
 * offset, and is never selected, copied or edited; it only displaces the glyphs that follow it. Everything
 * an area exposes — caret positions, selections, hit-testing, {@code getText()} — is unchanged by adding
 * one, which is what makes it safe to layer over a document model that other code indexes into.
 *
 * <p>Supply inlays through {@link GenericStyledArea#setInlayFactory(java.util.function.IntFunction)}.
 *
 * @see InlayIndex
 */
public final class Inlay {

    private final int column;
    private final String text;
    private final String styleClass;

    /**
     * @param column the document column to render before; clamped to the paragraph when out of range
     * @param text the label to draw
     * @param styleClass a CSS style class for the rendered node, or null for the default {@code inlay}
     */
    public Inlay(int column, String text, String styleClass) {
        this.column = column;
        this.text = Objects.requireNonNull(text, "text");
        this.styleClass = styleClass;
    }

    public Inlay(int column, String text) {
        this(column, text, null);
    }

    public int getColumn() {
        return column;
    }

    public String getText() {
        return text;
    }

    /** The extra CSS class for this inlay's node, or null. The node always carries {@code inlay}. */
    public String getStyleClass() {
        return styleClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Inlay)) {
            return false;
        }
        Inlay other = (Inlay) o;
        return column == other.column
                && text.equals(other.text)
                && Objects.equals(styleClass, other.styleClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(column, text, styleClass);
    }

    @Override
    public String toString() {
        return "Inlay@" + column + "(" + text + ")";
    }
}
