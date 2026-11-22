package za.co.mamadise.support_ticket_tracker.attachment;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Decides what an uploaded file really is from its bytes. The client's file name only selects which type is claimed
 * (by extension); the content must then match that type, so a renamed executable or HTML page is refused.
 * The client's Content-Type header is never used. Free of framework types so it can be tested in isolation.
 */
public final class FileTypeDetector {

    /** Allowed types: extension(s) and the content type the server stores and serves. */
    public enum AllowedType {
        PNG("image/png"),
        JPEG("image/jpeg"),
        PDF("application/pdf"),
        TEXT("text/plain");

        private final String contentType;

        AllowedType(String contentType) {
            this.contentType = contentType;
        }

        public String contentType() {
            return contentType;
        }
    }

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private FileTypeDetector() {
    }

    /** The type claimed by the file name's extension, if it is an allowed one. */
    public static Optional<AllowedType> fromExtension(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        return switch (filename.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "png" -> Optional.of(AllowedType.PNG);
            case "jpg", "jpeg" -> Optional.of(AllowedType.JPEG);
            case "pdf" -> Optional.of(AllowedType.PDF);
            case "txt", "log" -> Optional.of(AllowedType.TEXT);
            default -> Optional.empty();
        };
    }

    /** True when the content really is of the claimed type. */
    public static boolean contentMatches(AllowedType type, byte[] content) {
        if (content == null || content.length == 0) {
            return false;
        }
        return switch (type) {
            case PNG -> startsWith(content, PNG_MAGIC);
            case JPEG -> startsWith(content, JPEG_MAGIC);
            case PDF -> startsWith(content, PDF_MAGIC);
            case TEXT -> isPlainUtf8Text(content);
        };
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Valid UTF-8 with no NUL bytes and no other control characters except tab, line feed, carriage return and form
     * feed. Binary files fail this, and so do text files in legacy encodings (documented limitation).
     */
    static boolean isPlainUtf8Text(byte[] content) {
        for (byte b : content) {
            int c = b & 0xFF;
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r' && c != '\f') {
                return false;
            }
            if (c == 0x7F) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
