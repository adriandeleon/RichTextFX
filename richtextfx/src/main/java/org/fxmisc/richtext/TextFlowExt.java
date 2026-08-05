package org.fxmisc.richtext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.IndexRange;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import javafx.scene.text.HitInfo;
import javafx.scene.text.TextFlow;

/**
 * Adds additional API to {@link TextFlow}.
 */
class TextFlowExt extends TextFlow {

    private TextFlowLayout layout;

    /**
     * Inlay decorations injected into this flow, as a document&harr;flow index translation.
     *
     * <p><b>Two coordinate systems live in this class.</b> Everything above it — carets, selections, the
     * document model — counts only real characters (<i>document</i> indices). {@link TextFlowLayout} and
     * JavaFX's own {@code rangeShape}/{@code hitTest} count every managed child, and a child that is not a
     * {@link javafx.scene.text.Text} counts as one character (<i>flow</i> indices). With no inlays the two
     * are identical, which is why the {@code *Flow} internals below can be called directly on values that
     * already came out of the layout.
     *
     * <p>Methods taking an index translate on the way in; methods returning one translate on the way out.
     * See {@link InlayIndex}.
     */
    private InlayIndex inlays = InlayIndex.EMPTY;

    /*
     * Rename/Refactor to getLayoutInfo() and delete for JavaFX 25
     * Also delete TextFlowLayout and TextFlowSpan.
     */
    private TextFlowLayout textLayout()
    {
        if ( layout == null ) {
            layout = new TextFlowLayout( this );
        }
        return layout;
    }

    /** Declares the document columns at which inlay decoration nodes have been injected. */
    void setInlayIndex( InlayIndex index ) {
        this.inlays = (index == null) ? InlayIndex.EMPTY : index;
    }

    InlayIndex getInlayIndex() {
        return inlays;
    }

    int getLineCount() {
        return textLayout().getTextLineCount();
    }

    int getLineStartPosition(int charIdx) {
        return inlays.toDoc( getLineStartPositionFlow( inlays.toFlow( charIdx ) ) );
    }

    private int getLineStartPositionFlow(int flowIdx) {
        return textLayout().getTextLine( getLineOfCharacterFlow(flowIdx), false ).start();
    }

    int getLineEndPosition(int charIdx) {
        return inlays.toDoc( getLineEndPositionFlow( inlays.toFlow( charIdx ) ) );
    }

    private int getLineEndPositionFlow(int flowIdx) {
        int line = getLineOfCharacterFlow( flowIdx );
        int end = textLayout().getTextLine( line, false ).end();
        if ( line < (getLineCount() - 1) ) end--; // trailing space
        return end;
    }

    int getLineOfCharacter(int charIdx) {
        return getLineOfCharacterFlow( inlays.toFlow( charIdx ) );
    }

    private int getLineOfCharacterFlow(int flowIdx) {
        var layout = textLayout();
        return IntStream.range( 0, getLineCount() )
                .filter( l -> flowIdx < layout.getTextLine( l, false ).end() )
                .findFirst().orElse( Math.max(0,getLineCount()-1) );
    }

    PathElement[] getRangeShape(IndexRange range) {
        return getRangeShape(range.getStart(), range.getEnd());
    }

    PathElement[] getRangeShape(int from, int to) {
        return rangeShape(inlays.toFlow(from), inlays.toFlow(to));
    }

    /**
     * A range shape for indices that are <b>already</b> in flow coordinates. Used by
     * {@link ParagraphText}'s background/border/underline pass, which walks the children itself and so
     * counts in the same units the layout does.
     */
    PathElement[] getRangeShapeFlow(int from, int to) {
        return rangeShape(from, to);
    }

    /**
     * Note that this doesn't call super.getUnderlineShape in JavaFX 25+
     */
    PathElement[] getUnderlineShape(IndexRange range) {
        return getUnderlineShape(range.getStart(), range.getEnd(), 0, 0, 0);
    }

    /**
     * Note that this doesn't call super.getUnderlineShape in JavaFX 25+
     *
     * @param from The index of the first character.
     * @param to The index of the last character.
     * @param offset The distance below the baseline to draw the underline.
     * @param waveRadius If non-zero, draw a wavy underline with arcs of this radius.
     * @return An array with the PathElement objects which define an
     *         underline from the first to the last character.
     */
    PathElement[] getUnderlineShape(int from, int to, double offset, double waveRadius, double doubleGap) {
        return getUnderlineShapeFlow( inlays.toFlow(from), inlays.toFlow(to), offset, waveRadius, doubleGap );
    }

    /** As {@link #getUnderlineShape}, for indices already in flow coordinates. */
    PathElement[] getUnderlineShapeFlow(int from, int to, double offset, double waveRadius, double doubleGap) {
        // get a Path for the text underline
        List<PathElement> result = new ArrayList<>();

        PathElement[] shape = rangeShape( from, to );
        // The shape is a closed Path for one or more rectangles AROUND the selected text.
        // shape: [MoveTo origin, LineTo top R, LineTo bottom R, LineTo bottom L, LineTo origin, *]

        boolean doubleLine = (doubleGap > 0.0);
        List<PathElement> result2 = new ArrayList<>();

        // Extract the bottom left and right coordinates for each rectangle to get the underline path.
        for ( int ele = 2; ele < shape.length; ele += 5 )
        {
            LineTo bl = (LineTo) shape[ele+1];
            LineTo br = (LineTo) shape[ele];

            double y = snapSizeY( br.getY() + offset - 2.5 );
            double leftx = snapSizeX( bl.getX() );

            if (waveRadius <= 0) {
                result.add(new MoveTo( leftx, y ));
                result.add(new LineTo( snapSizeX( br.getX() ), y ));
                if (doubleLine) {
                    y += doubleGap;
                    result2.add(new MoveTo( leftx, y ));
                    result2.add(new LineTo( snapSizeX( br.getX() ), y ));
                }
            }
            else {
                // For larger wave radii increase the X radius to stretch out the wave.
                double radiusX = waveRadius > 1 ? waveRadius * 1.25 : waveRadius;
                double rightx = br.getX();
                result.add(new MoveTo( leftx, y ));
                if (doubleLine) {
                    result2.add(new MoveTo( leftx, y+doubleGap ));
                }
                boolean sweep = true;
                while ( leftx < rightx ) {
                    leftx += waveRadius * 2;

                    if (leftx > rightx) {
                        // Since we are drawing the wave in segments, it is necessary to
                        // clip the final arc to avoid over/underflow with larger radii,
                        // so we must compute the y value for the point on the arc where
                        // x = rightx.
                        // To simplify the computation, we translate so that the center of
                        // the arc has x = 0, and the known endpoints have y = 0.
                        double dx = rightx - (leftx - waveRadius);
                        double dxsq = dx * dx;
                        double rxsq = radiusX * radiusX;
                        double rysq = waveRadius * waveRadius;
                        double dy = waveRadius * (Math.sqrt(1 - dxsq/rxsq) - Math.sqrt(1 - rysq/rxsq));

                        if (sweep) y -= dy; else y += dy;
                        leftx = rightx;
                    }
                    result.add(new ArcTo( radiusX, waveRadius, 0.0, leftx, y, false, sweep ));
                    if (doubleLine) {
                        result2.add(new ArcTo( radiusX, waveRadius, 0.0, leftx, y+doubleGap, false, sweep ));
                    }
                    sweep = !sweep;
                }
            }
        }

        if (doubleLine) result.addAll( result2 );

        return result.toArray(new PathElement[0]);
    }

    CharacterHit hitLine(double x, int lineIndex) {
        Rectangle2D r = textLayout().getTextLine( lineIndex, false ).bounds();
        double y = r.getMinY() + r.getHeight() / 2.0;
        return hit( x, y, lineIndex );
    }

    CharacterHit hit(double x, double y) {
        var layout = textLayout();
        int line = IntStream.range( 0, getLineCount() )
                    .filter( l -> y < layout.getTextLine( l, true ).bounds().getMaxY() )
                    .findFirst().orElse( Math.max(0,getLineCount()-1) );
        return hit( x, y, line );
    }

    CharacterHit hit(double x, double y, int line) {

        Rectangle2D lineBounds = textLayout().getTextLine( line, false ).bounds();
        HitInfo hit = hitTest(new Point2D(x, y));
        // hitTest speaks flow indices; every comparison below is against layout values, so the whole
        // method stays in flow coordinates and converts only where a CharacterHit is built.
        int charIdx = hit.getCharIndex();
        boolean leading = hit.isLeading();

        if (y >= lineBounds.getMaxY()) {
            return CharacterHit.insertionAt(inlays.toDoc(charIdx));
        }

        if ( ! leading && getLineCount() > 1) {
            // If this is a wrapped paragraph and hit character is at end of hit line, make sure that the
            // "character hit" stays at the end of the hit line (and not at the beginning of the next line).
            leading = (getLineOfCharacterFlow(charIdx) + 1 < getLineCount() && charIdx + 1 >= textLayout().getTextLine( line, false ).end());
        }

        if(x < lineBounds.getMinX() || x > lineBounds.getMaxX()) {
            if(leading) {
                return CharacterHit.insertionAt(inlays.toDoc(charIdx));
            } else {
                return CharacterHit.insertionAt(inlays.toDoc(charIdx + 1));
            }
        } else {
            if(leading) {
                return CharacterHit.leadingHalfOf(inlays.toDoc(charIdx));
            } else {
                return CharacterHit.trailingHalfOf(inlays.toDoc(charIdx));
            }
        }
    }

}
