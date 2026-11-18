package org.fxmisc.richtext;

import static org.fxmisc.richtext.ClipboardHelper.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

import javafx.scene.control.IndexRange;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;

import org.fxmisc.richtext.model.Codec;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.StyledSegment;
import org.reactfx.util.Tuple2;

/**
 * Clipboard actions for {@link TextEditingArea}.
 */
public interface ClipboardActions<PS, SEG, S> extends EditActions<PS, SEG, S> {

    /**
     * Gets codecs to encode/decode style information to/from binary format.
     * Providing codecs enables clipboard actions to retain the style information.
     */
    Optional<Tuple2<Codec<PS>, Codec<StyledSegment<SEG, S>>>> getStyleCodecs();
    void setStyleCodecs(Codec<PS> paragraphStyleCodec, Codec<StyledSegment<SEG, S>> textStyleCodec);

    SegmentOps<SEG, S> getSegOps();

    /**
     * Transfers the currently selected text to the clipboard,
     * removing the current selection.
     */
    default void cut() {
        copy();
        IndexRange selection = getSelection();
        deleteText(selection.getStart(), selection.getEnd());
    }

    /**
     * Transfers the currently selected text to the clipboard,
     * leaving the current selection.
     */
    default void copy() {
        IndexRange selection = getSelection();
        if(selection.getLength() > 0) {
            ClipboardContent content = new ClipboardContent();

            content.putString(getSelectedText());

            getStyleCodecs().ifPresent(codecs -> {
                Codec<StyledDocument<PS, SEG, S>> codec = ReadOnlyStyledDocument.codec(codecs._1, codecs._2, getSegOps());
                DataFormat format = dataFormat(codec.getName());
                StyledDocument<PS, SEG, S> doc = subDocument(selection.getStart(), selection.getEnd());
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(os);
                try {
                    codec.encode(dos, doc);
                    content.put(format, os.toByteArray());
                } catch (IOException e) {
                    System.err.println("Codec error: Exception in encoding '" + codec.getName() + "':");
                    e.printStackTrace();
                }
            });

            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    /**
     * Inserts the content from the clipboard into this text-editing area,
     * replacing the current selection. If there is no selection, the content
     * from the clipboard is inserted at the current caret position.
     */
    default void paste() {
        Clipboard clipboard = Clipboard.getSystemClipboard();

        if(getStyleCodecs().isPresent()) {
            Tuple2<Codec<PS>, Codec<StyledSegment<SEG, S>>> codecs = getStyleCodecs().get();
            Codec<StyledDocument<PS, SEG, S>> codec = ReadOnlyStyledDocument.codec(codecs._1, codecs._2, getSegOps());
            DataFormat format = dataFormat(codec.getName());
            byte[] bytes = clipboard.hasContent(format) ? bytesOf(clipboard.getContent(format)) : null;
            if(bytes != null) {
                ByteArrayInputStream is = new ByteArrayInputStream(bytes);
                DataInputStream dis = new DataInputStream(is);
                StyledDocument<PS, SEG, S> doc = null;
                try {
                    doc = codec.decode(dis);
                } catch (IOException e) {
                    System.err.println("Codec error: Failed to decode '" + codec.getName() + "':");
                    e.printStackTrace();
                }
                if(doc != null) {
                    replaceSelection(doc);
                    return;
                }
            }
        }

        if (clipboard.hasString()) {
            String text = clipboard.getString();
            if (text != null) {
				text = removeEndingNullChars(text);
                replaceSelection(text);
            }
        }
    }
	
	/**
	 * Removes null characters that were appended to clipboard text on Windows, e.g. when copying from Microsoft Word.
	 */
	private String removeEndingNullChars(String text) {
		if (text.endsWith("\u0000")) {
			return text.replaceAll("\u0000+\\Z", "");
		}
		return text;
	}
}

class ClipboardHelper {
    static DataFormat dataFormat(String name) {
        DataFormat format = DataFormat.lookupMimeType(name);
        if(format != null) {
            return format;
        } else {
            return new DataFormat(name);
        }
    }

    /** The first four bytes of a Java serialization stream: magic {@code 0xACED}, version 5. */
    private static final int SERIALIZED = 0xACED0005;

    /**
     * The bytes of a custom-format clipboard value; null when it holds none, so paste falls back to text.
     * The area puts a {@code byte[]}, but the platform may hand it back as a {@link ByteBuffer}, and that
     * buffer may hold the array's Java serialization rather than the array (JavaFX 27.0.1's headless clipboard
     * does). A styled document never starts with that header (its first int is a paragraph count, never
     * negative), so the header means "unwrap". Only a lone {@code byte[]} is ever deserialized.
     */
    static byte[] bytesOf(Object content) {
        byte[] bytes;
        if(content instanceof byte[]) {
            bytes = (byte[]) content;
        } else if(content instanceof ByteBuffer) {
            ByteBuffer view = ((ByteBuffer) content).duplicate();
            bytes = new byte[view.remaining()];
            view.get(bytes);
        } else {
            return null;
        }
        return isSerialized(bytes) ? deserializedBytes(bytes) : bytes;
    }

    private static boolean isSerialized(byte[] bytes) {
        return bytes.length >= 4 && ByteBuffer.wrap(bytes).getInt() == SERIALIZED;
    }

    /** The {@code byte[]} serialized in {@code bytes}, or null when it is anything else. */
    private static byte[] deserializedBytes(byte[] bytes) {
        try(ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(info -> {
                Class<?> type = info.serialClass();
                if(info.depth() > 1 || info.references() > 4 || info.arrayLength() > bytes.length) {
                    return ObjectInputFilter.Status.REJECTED; // one array, no larger than the stream itself
                }
                return type == null || type == byte[].class
                        ? ObjectInputFilter.Status.ALLOWED
                        : ObjectInputFilter.Status.REJECTED;
            });
            Object value = in.readObject();
            return value instanceof byte[] ? (byte[]) value : null;
        } catch(IOException | ClassNotFoundException | RuntimeException e) {
            return null;
        }
    }
}
