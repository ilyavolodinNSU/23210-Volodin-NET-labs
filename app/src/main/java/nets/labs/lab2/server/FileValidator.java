package nets.labs.lab2.server;

import java.io.File;
import java.io.IOException;

public class FileValidator {
    public static void validateFileName(File uploadsDir, File targetFile, String fileName) throws IOException {
        String canonicalPath = targetFile.getCanonicalPath();
        String uploadsCanonicalPath = uploadsDir.getCanonicalPath();
        if (!canonicalPath.startsWith(uploadsCanonicalPath)) {
            throw new SecurityException("Invalid filename: " + fileName);
        }
    }
    
    public static void validateFileSize(long expectedSize, long actualSize) throws IOException {
        if (actualSize != expectedSize) {
            throw new IOException("File size mismatch: expected " + expectedSize + ", got " + actualSize);
        }
    }
    
    public static void validateFileNameLength(int fileNameLength) {
        if (fileNameLength > Constants.MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException("Filename length exceeds " + Constants.MAX_FILENAME_LENGTH + " bytes");
        }
    }
}