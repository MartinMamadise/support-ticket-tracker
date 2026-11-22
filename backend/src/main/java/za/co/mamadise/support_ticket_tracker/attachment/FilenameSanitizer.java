package za.co.mamadise.support_ticket_tracker.attachment;

import java.text.Normalizer;

/**
 * Turns a client-supplied file name into a safe display name. The result is only ever shown to users and sent back in
 * Content-Disposition; it is never used to build a path (files are stored under a random key).
 * Free of framework types so it can be tested in isolation.
 */
public final class FilenameSanitizer {

    public static final int MAX_LENGTH = 150;

    private FilenameSanitizer() {
    }

    /**
     * Keeps only the last path segment (both / and \ separators), removes control characters and characters that are
     * unsafe in file names or headers, collapses whitespace, strips leading dots (no hidden files, no "..") and limits
     * the length while keeping the extension. Returns {@code fallback} when nothing usable is left.
     */
    public static String sanitize(String original, String fallback) {
        if (original == null) {
            return fallback;
        }
        String name = Normalizer.normalize(original, Normalizer.Form.NFC);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder out = new StringBuilder(name.length());
        name.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp) || "<>:\"/\\|?*;".indexOf(cp) >= 0
                    || Character.getType(cp) == Character.FORMAT) {
                return;
            }
            out.appendCodePoint(Character.isWhitespace(cp) ? ' ' : cp);
        });
        String cleaned = out.toString().replaceAll(" +", " ").trim();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1).trim();
        }
        if (cleaned.isEmpty()) {
            return fallback;
        }
        if (cleaned.codePointCount(0, cleaned.length()) > MAX_LENGTH) {
            int dot = cleaned.lastIndexOf('.');
            String extension = dot > 0 && cleaned.length() - dot <= 10 ? cleaned.substring(dot) : "";
            String base = cleaned.substring(0, cleaned.length() - extension.length());
            int keep = MAX_LENGTH - extension.length();
            base = base.substring(0, base.offsetByCodePoints(0, Math.min(keep, base.codePointCount(0, base.length()))));
            cleaned = base.trim() + extension;
        }
        return cleaned;
    }
}
