package org.fxmisc.richtext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

/** The styled-text clipboard value is read whether the platform returns a {@code byte[]} or a {@link ByteBuffer}. */
public class ClipboardBytesTest {

    @Test
    public void aByteArrayIsUsedAsIs() {
        byte[] bytes = {1, 2, 3};
        assertSame(bytes, ClipboardHelper.bytesOf(bytes));
    }

    @Test
    public void aByteBufferGivesItsRemainingBytesAndKeepsItsPosition() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[] {9, 1, 2, 3});
        buffer.get(); // a buffer that was read from already: only what remains is the value
        assertArrayEquals(new byte[] {1, 2, 3}, ClipboardHelper.bytesOf(buffer));
        assertEquals(1, buffer.position(), "the platform's buffer is left as it was");
        assertArrayEquals(new byte[] {1, 2, 3}, ClipboardHelper.bytesOf(buffer.asReadOnlyBuffer()));
    }

    @Test
    public void aSerializedByteArrayIsUnwrapped() throws IOException {
        // what JavaFX 27.0.1's headless clipboard returns for a byte[] put under a custom format
        byte[] payload = {1, 2, 3, 4, 5, (byte) 200};
        assertArrayEquals(payload, ClipboardHelper.bytesOf(ByteBuffer.wrap(serialized(payload))));
        assertArrayEquals(payload, ClipboardHelper.bytesOf(serialized(payload)));
    }

    @Test
    public void aSerializedObjectThatIsNotAByteArrayIsNeverRead() throws IOException {
        ArrayList<String> other = new ArrayList<>();
        other.add("x");
        assertNull(ClipboardHelper.bytesOf(ByteBuffer.wrap(serialized(other))));
        assertNull(ClipboardHelper.bytesOf(ByteBuffer.wrap(serialized("text"))));
        byte[] cut = serialized(new byte[] {1, 2, 3});
        assertNull(ClipboardHelper.bytesOf(java.util.Arrays.copyOf(cut, cut.length - 2)), "a truncated stream");
    }

    @Test
    public void anythingElseIsNoStyledText() {
        assertNull(ClipboardHelper.bytesOf("text"));
        assertNull(ClipboardHelper.bytesOf(null));
    }

    private static byte[] serialized(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }
}
