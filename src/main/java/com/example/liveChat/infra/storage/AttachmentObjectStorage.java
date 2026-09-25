package com.example.liveChat.infra.storage;

public interface AttachmentObjectStorage {
    SignedUpload signUpload(String objectKey, String contentType, long contentLength, String ownerId, String uploadId);

    SignedDownload signDownload(String objectKey, String originalName, String contentType);

    StoredObjectMetadata inspect(String objectKey, String contentType);

    void delete(String objectKey, String contentType);
}
