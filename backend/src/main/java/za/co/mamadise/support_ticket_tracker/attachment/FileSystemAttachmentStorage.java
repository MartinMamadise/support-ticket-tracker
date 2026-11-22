package za.co.mamadise.support_ticket_tracker.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stores attachments as plain files in one directory ({@code app.attachments.dir}, env {@code ATTACHMENTS_DIR}),
 * which must be outside anything a web server publishes. File names are the server-generated keys only (UUID format is
 * enforced), and the resolved path is checked to stay inside the directory, so no client input can reach the
 * file system path.
 */
@Component
public class FileSystemAttachmentStorage implements AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(FileSystemAttachmentStorage.class);
    private static final Pattern KEY = Pattern.compile("^[0-9a-f]{32}$");

    private final Path root;

    public FileSystemAttachmentStorage(@Value("${app.attachments.dir:./data/attachments}") String directory)
            throws IOException {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Attachment storage directory: {}", root);
    }

    @Override
    public void store(String key, byte[] content) throws IOException {
        Path target = resolve(key);
        // CREATE_NEW: never overwrite an existing file.
        Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key), StandardOpenOption.READ);
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
