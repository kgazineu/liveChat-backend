package com.example.liveChat.infra.storage;

public class AttachmentStorageException extends RuntimeException {
    public AttachmentStorageException(String operation, Throwable cause) {
        super("Attachment object storage operation failed: " + operation, cause);
    }

    public AttachmentStorageException(String operation) {
        super("Attachment object storage operation failed: " + operation);
    }

    protected AttachmentStorageException(String message, Throwable cause, boolean directMessage) {
        super(message, cause);
    }
}
