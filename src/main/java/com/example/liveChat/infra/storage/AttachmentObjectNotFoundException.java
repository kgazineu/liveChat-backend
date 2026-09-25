package com.example.liveChat.infra.storage;

public class AttachmentObjectNotFoundException extends AttachmentStorageException {
    public AttachmentObjectNotFoundException() {
        super("Upload has not been completed or is no longer available", null, true);
    }

    public AttachmentObjectNotFoundException(Throwable cause) {
        super("Upload has not been completed or is no longer available", cause, true);
    }
}
