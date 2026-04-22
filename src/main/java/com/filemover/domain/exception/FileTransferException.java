package com.filemover.domain.exception;

public class FileTransferException extends RuntimeException {

    private final String fileName;

    public FileTransferException(String fileName, String message) {
        super("File transfer failed for [%s]: %s".formatted(fileName, message));
        this.fileName = fileName;
    }

    public FileTransferException(String fileName, String message, Throwable cause) {
        super("File transfer failed for [%s]: %s".formatted(fileName, message), cause);
        this.fileName = fileName;
    }

    public String getFileName() { return fileName; }
}
