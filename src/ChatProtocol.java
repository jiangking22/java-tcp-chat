import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;

final class ChatProtocol {
    static final int MAX_FRAME_BYTES = 4096;

    private ChatProtocol() {
    }

    static PrintWriter writer(OutputStream stream) {
        return new PrintWriter(stream, true, StandardCharsets.UTF_8);
    }

    static final class FrameReader {
        private final InputStream stream;
        private final byte[] frame = new byte[MAX_FRAME_BYTES];
        private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();

        FrameReader(InputStream stream) {
            this.stream = new BufferedInputStream(stream);
        }

        String readLine() throws IOException {
            int length = 0;
            int next;
            while ((next = stream.read()) != -1) {
                if (next == '\n') {
                    return decode(length);
                }
                if (next == '\r') {
                    int following = stream.read();
                    if (following == '\n') {
                        return decode(length);
                    }
                    if (following == -1) {
                        return null;
                    }
                    throw new FrameException("Error: unexpected carriage return in frame.");
                }
                if (length == frame.length) {
                    throw oversizedFrame();
                }
                frame[length++] = (byte) next;
            }
            // Only complete LF- or CRLF-terminated frames are delivered.
            return null;
        }

        private String decode(int length) throws FrameException {
            try {
                return decoder.decode(ByteBuffer.wrap(frame, 0, length)).toString();
            } catch (CharacterCodingException invalid) {
                throw new FrameException("Error: invalid UTF-8 frame.");
            }
        }

        private static FrameException oversizedFrame() {
            return new FrameException("Error: frame exceeds " + MAX_FRAME_BYTES + " UTF-8 bytes.");
        }
    }

    static final class FrameException extends IOException {
        private static final long serialVersionUID = 1L;

        private FrameException(String message) {
            super(message);
        }
    }
}
