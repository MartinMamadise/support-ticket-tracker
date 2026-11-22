package za.co.mamadise.support_ticket_tracker.attachment;

import java.io.InputStream;

/** An authorised download: the stream and the stored, trusted metadata to send with it. */
public record AttachmentDownload(String filename, String contentType, long sizeBytes, InputStream content) {
}
