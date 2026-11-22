package za.co.mamadise.support_ticket_tracker.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FilenameSanitizerTest {

    private static String clean(String name) {
        return FilenameSanitizer.sanitize(name, "attachment.txt");
    }

    @Test
    void pathsAreReducedToTheLastSegment() {
        assertThat(clean("../../etc/passwd.txt")).isEqualTo("passwd.txt");
        assertThat(clean("C:\\Users\\me\\Desktop\\error.log")).isEqualTo("error.log");
        assertThat(clean("/var/log/app.log")).isEqualTo("app.log");
    }

    @Test
    void unsafeCharactersAndHiddenFileDotsAreRemoved() {
        assertThat(clean("re<port>:\"x\"|?*;.pdf")).isEqualTo("report x.pdf".replace(" ", ""));
        assertThat(clean("..hidden.txt")).isEqualTo("hidden.txt");
        assertThat(clean("line\r\nbreak\u0000.txt")).isEqualTo("linebreak.txt");
        assertThat(clean("rtl\u202Etxt.exe.png")).as("bidi override removed").isEqualTo("rtltxt.exe.png");
        assertThat(clean("  many   spaces .png ")).isEqualTo("many spaces .png");
    }

    @Test
    void emptyResultsFallBack() {
        assertThat(clean(null)).isEqualTo("attachment.txt");
        assertThat(clean("")).isEqualTo("attachment.txt");
        assertThat(clean("../..")).isEqualTo("attachment.txt");
        assertThat(clean("....")).isEqualTo("attachment.txt");
    }

    @Test
    void longNamesAreShortenedButKeepTheirExtension() {
        String result = clean("a".repeat(400) + ".pdf");
        assertThat(result).hasSize(FilenameSanitizer.MAX_LENGTH).endsWith(".pdf");
    }
}
