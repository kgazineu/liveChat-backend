package com.example.liveChat.infra.storage;

public interface AttachmentObjectStorage {
    SignedUpload presignPut(String objectKey, String contentType, long contentLength, String ownerId, String uploadId);

    SignedDownload presignGet(String objectKey);

    StoredObjectMetadata head(String objectKey);

    void delete(String objectKey);
}
