package org.fxmisc.richtext.model;

import static org.fxmisc.richtext.model.ReadOnlyStyledDocument.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.reactfx.util.Tuple3;

public class ReadOnlyStyledDocumentTest {
    private static final Void NULL = new Void();
    private static final String TEST_TEXT = 
                "To be, or not to be, that is the question:\n"
               +"Whether 'tis nobler in the mind to suffer\n"
               +"The slings and arrows of outrageous fortune,\n"
               +"Or to take arms against a sea of troubles,";

    /** Short for Void:
     * cannot pass in 'null' since compiler will interpret it as a StyleSpans argument to Paragraph's constructor */
    private static class Void { }

    @Test
    public void testUndo() {
        TextOps<String, String> segOps = SegmentOps.styledTextOps();
        ReadOnlyStyledDocument<String, String, String> doc0 = fromString("", "X", "X", segOps);

        doc0.replace(0, 0, fromString("abcd", "Y", "Y", segOps)).exec((doc1, chng1, pchng1) -> {
            // undo chng1
            doc1.replace(chng1.getPosition(), chng1.getInsertionEnd(), from(chng1.getRemoved())).exec((doc2, chng2, pchng2) -> {
                // we should have arrived at the original document
                assertEquals(doc0, doc2);

                // chng2 should be the inverse of chng1
                assertEquals(chng1.invert(), chng2);
            });
        });
    }

    @Test
    public void testMultiParagraphFromSegment() {
        TextOps<String, Void> segOps = SegmentOps.styledTextOps();
        ReadOnlyStyledDocument<Void, String, Void> doc0 = fromSegment("Foo\nBar", NULL, NULL, segOps);
        assertEquals( 2, doc0.getParagraphCount() );
    }

    @Test
    public void deleteNewlineTest() {
        TextOps<String, Void> segOps = SegmentOps.styledTextOps();
        ReadOnlyStyledDocument<Void, String, Void> doc0 = fromString("Foo\nBar", NULL, NULL, segOps);
        doc0.replace(3, 4, fromString("", NULL, NULL, segOps)).exec((doc1, ch, pch) -> {
            List<? extends Paragraph<Void, String, Void>> removed = pch.getRemoved();
            List<? extends Paragraph<Void, String, Void>> added = pch.getAdded();
            assertEquals(2, removed.size());
            Paragraph<Void, String, Void> p = new Paragraph<>(NULL, segOps, segOps.create("som"), NULL);
            assertEquals(new Paragraph<>(NULL, segOps, "Foo", NULL), removed.get(0));
            assertEquals(new Paragraph<>(NULL, segOps, "Bar", NULL), removed.get(1));
            assertEquals(1, added.size());
            assertEquals(new Paragraph<>(NULL, segOps, "FooBar", NULL), added.get(0));
        });
    }

    @Test
    public void evaluateEmptyTextPosition() {
        TextOps<String, Void> segOps = SegmentOps.styledTextOps();
        ReadOnlyStyledDocument<Void, String, Void> document = fromString("", NULL, NULL, segOps);
        assertEquals(0, document.position(0, 0).toOffset());
        assertEquals(-1, document.position(0, -1).toOffset());
        assertEquals(-1, document.position(0, -1).clamp().toOffset());
        assertEquals(1, document.position(0, 1).toOffset());
        assertEquals(-1, document.position(0, 1).clamp().toOffset()); // This looks like a bug, but that is current behaviour
        assertEquals(1, document.position(1, 0).toOffset());
        assertEquals(1, document.position(1, 0).clamp().toOffset());
        assertThrows(IndexOutOfBoundsException.class, () -> document.position(2, 0).toOffset());
    }

    @Test
    public void evaluatePosition() {
        TextOps<String, Void> segOps = SegmentOps.styledTextOps();
        ReadOnlyStyledDocument<Void, String, Void> document = fromString(TEST_TEXT, NULL, NULL, segOps);
        assertEquals(2, document.position(0, 2).toOffset());
        assertEquals(42, document.position(0, 42).toOffset());
        assertEquals(43, document.position(1, 0).toOffset());
        assertEquals(45, document.position(1, 2).toOffset());
        assertEquals(87, document.position(2, 2).toOffset());
        assertEquals(132, document.position(3, 2).toOffset());
        assertEquals(132, document.position(3, 2).clamp().toOffset());
        assertEquals(172, document.position(3, 42).toOffset()); // End of the line
        assertThrows(IndexOutOfBoundsException.class, () -> document.position(5, 0).toOffset());
        assertThrows(IndexOutOfBoundsException.class, () -> document.position(-1, 0).toOffset());

        // Following is covering current behaviour to avoid breaking, but these are quite strange
        // The clamp is wrong compared to the specification of the method which states: "if the position is beyond the
        // end of a given paragraph, moves the position back to the end of the paragraph", but these are tests meant to
        // define current behaviour. If you ever fix it and see these tests failing, you can fix the tests.
        assertEquals(-1, document.position(0, -1).toOffset());
        assertEquals(-1, document.position(0, -1).clamp().toOffset());
        assertEquals(43, document.position(0, 43).toOffset()); // First paragraph has 42 chars
        assertEquals(43, document.position(0, 43).clamp().toOffset());
        assertEquals(44, document.position(0, 44).toOffset());
        assertEquals(44, document.position(0, 44).clamp().toOffset());
        assertEquals(42, document.position(1, -1).toOffset());
        assertEquals(42, document.position(1, -1).clamp().toOffset());
        assertEquals(173, document.position(4, 0).toOffset()); // There are only 3 paragraphs
        assertEquals(173, document.position(3, 43).toOffset()); // After the end of the line it continues for some reason
        assertEquals(171, document.position(3, 43).clamp().toOffset());
    }

    @Test
    public void replaceTextContent() {
        TextOps<String, Void> segOps = SegmentOps.styledTextOps();
        ReadOnlyStyledDocument<Void, String, Void> document = fromString(TEST_TEXT, NULL, NULL, segOps);
        ReadOnlyStyledDocument<Void, String, Void> replace = fromString(" try to ", NULL, NULL, segOps);
        Tuple3<ReadOnlyStyledDocument<Void, String, Void>, ?, ?> tuple = document.replace(2, 136, replace);
        assertEquals("To try to take arms against a sea of troubles,", tuple._1.getText());
    }

    @Test
    public void testRestyle() {
        // texts
        final String fooBar = "Foo Bar";
        final String and = " and ";
        final String helloWorld = "Hello World";

        // styles
        final String bold = "bold";
        final String empty = "";
        final String italic = "italic";
        TextOps<String, String> segOps = SegmentOps.styledTextOps();

        SimpleEditableStyledDocument<String, String> doc0 = new SimpleEditableStyledDocument<>("", "");

        BiConsumer<String, String> appendStyledText = (text, style) -> {
            ReadOnlyStyledDocument<String, String, String> rosDoc = fromString(text, "", style, segOps);
            doc0.replace(doc0.getLength(), doc0.getLength(), rosDoc);
        };

        appendStyledText.accept(fooBar, bold);
        appendStyledText.accept(and, empty);
        appendStyledText.accept(helloWorld, bold);

        StyleSpans<String> styles = doc0.getStyleSpans(4,  17);
        assertEquals(3, styles.getSpanCount(), "Invalid number of Spans");

        StyleSpans<String> newStyles = styles.mapStyles(style -> italic);
        doc0.setStyleSpans(4, newStyles);

        // assert the new segment structure:
        //  StyledText[text="Foo ", style=bold]
        //  StyledText[text="Bar and Hello", style=italic]
        //  StyledText[text=" World", style=bold]
        StyleSpans<String> spans = doc0.getParagraphs().get(0).getStyleSpans();

        assertEquals(3, spans.getSpanCount());
        assertEquals(bold, spans.getStyleSpan(0).getStyle());
        assertEquals(italic, spans.getStyleSpan(1).getStyle());
        assertEquals(bold, spans.getStyleSpan(2).getStyle());
    }


    private static boolean hasCachedText(Paragraph<?, ?, ?> p) throws Exception {
        java.lang.reflect.Field f = Paragraph.class.getDeclaredField("text");
        f.setAccessible(true);
        return f.get(p) != null;
    }

    /** Whole-document text is built from the segments; a multi-segment paragraph keeps no private copy. */
    @Test
    public void getTextJoinsParagraphsWithoutCachingMultiSegmentParagraphText() throws Exception {
        TextOps<String, String> noJoinOps = new TextOps<>() {
            @Override public int length(String s) { return s.length(); }
            @Override public char charAt(String s, int index) { return s.charAt(index); }
            @Override public String getText(String s) { return s; }
            @Override public String subSequence(String s, int start, int end) { return s.substring(start, end); }
            @Override public String subSequence(String s, int start) { return s.substring(start); }
            @Override public java.util.Optional<String> joinSeg(String currentSeg, String nextSeg) { return java.util.Optional.empty(); }
            @Override public String createEmptySeg() { return ""; }
            @Override public String create(String text) { return text; }
        };
        ReadOnlyStyledDocument<String, String, String> doc = fromString("one\n\nthree", "ps", "a", noJoinOps)
                .replace(1, 2, fromString("N", "ps", "b", noJoinOps)).get1();
        Paragraph<String, String, String> first = doc.getParagraph(0);
        assertEquals(3, first.getSegments().size());
        assertEquals(1, doc.getParagraph(2).getSegments().size());

        assertEquals("oNe\n\nthree", doc.getText());
        assertEquals(doc.length(), doc.getText().length());
        assertFalse(hasCachedText(first), "multi-segment paragraph must not cache a copy on a whole-document read");
        assertTrue(hasCachedText(doc.getParagraph(2)), "single-segment paragraph cache is the segment itself");
        assertSame(doc.getText(), doc.getText());
        assertEquals("oNe", first.getText());
    }

    @Test
    public void getTextOfSinglePlainParagraphDocumentIsTheSegment() {
        TextOps<String, String> segOps = SegmentOps.styledTextOps();
        String line = new String("plain");
        ReadOnlyStyledDocument<String, String, String> doc = fromSegment(line, "ps", "s", segOps);
        assertSame(line, doc.getParagraph(0).getText());
        assertEquals("plain", doc.getText());
    }
}
