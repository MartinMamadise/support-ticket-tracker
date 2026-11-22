package za.co.mamadise.support_ticket_tracker.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import za.co.mamadise.support_ticket_tracker.attachment.FileTypeDetector.AllowedType;

class FileTypeDetectorTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    static final byte[] PDF = "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Test
    void onlyTheAllowedExtensionsAreAccepted() {
        assertThat(FileTypeDetector.fromExtension("shot.PNG")).contains(AllowedType.PNG);
        assertThat(FileTypeDetector.fromExtension("photo.jpeg")).contains(AllowedType.JPEG);
        assertThat(FileTypeDetector.fromExtension("photo.jpg")).contains(AllowedType.JPEG);
        assertThat(FileTypeDetector.fromExtension("manual.pdf")).contains(AllowedType.PDF);
        assertThat(FileTypeDetector.fromExtension("server.log")).contains(AllowedType.TEXT);
        assertThat(FileTypeDetector.fromExtension("notes.txt")).contains(AllowedType.TEXT);
        for (String bad : new String[] {"setup.exe", "page.html", "image.svg", "script.js", "noext", "trailing.", null,
                "archive.png.exe", "shell.php"}) {
            assertThat(FileTypeDetector.fromExtension(bad)).as(String.valueOf(bad)).isEmpty();
        }
    }

    @Test
    void contentMustMatchTheClaimedType() {
        assertThat(FileTypeDetector.contentMatches(AllowedType.PNG, PNG)).isTrue();
        assertThat(FileTypeDetector.contentMatches(AllowedType.JPEG, JPEG)).isTrue();
        assertThat(FileTypeDetector.contentMatches(AllowedType.PDF, PDF)).isTrue();
        assertThat(FileTypeDetector.contentMatches(AllowedType.PNG, PDF)).as("renamed PDF").isFalse();
        assertThat(FileTypeDetector.contentMatches(AllowedType.PDF, "<html><script>".getBytes(StandardCharsets.UTF_8)))
                .isFalse();
        assertThat(FileTypeDetector.contentMatches(AllowedType.PNG, new byte[0])).isFalse();
    }

    @Test
    void textMustBeUtf8WithoutBinaryControlCharacters() {
        assertThat(FileTypeDetector.contentMatches(AllowedType.TEXT,
                "2026-11-21 ERROR Disk full\r\n\tat line 3 – ünïcödé".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(FileTypeDetector.contentMatches(AllowedType.TEXT, new byte[] {'M', 'Z', 0, 0})).as("executable")
                .isFalse();
        assertThat(FileTypeDetector.contentMatches(AllowedType.TEXT, new byte[] {(byte) 0xC3, (byte) 0x28}))
                .as("invalid UTF-8").isFalse();
        assertThat(FileTypeDetector.contentMatches(AllowedType.TEXT, PNG)).isFalse();
    }
}
