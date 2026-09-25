package com.example.liveChat.infra.storage;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.presigner.PresignedRequest;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Profile("!test")
public class S3AttachmentObjectStorage implements AttachmentObjectStorage {
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration API_ATTEMPT_TIMEOUT = Duration.ofSeconds(5);

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration uploadUrlTtl;
    private final Duration downloadUrlTtl;

    public S3AttachmentObjectStorage(S3AttachmentStorageProperties properties) {
        URI internalEndpoint = endpoint(properties.getInternalEndpoint(), "internal-endpoint");
        URI publicEndpoint = endpoint(properties.getPublicEndpoint(), "public-endpoint");
        Region region = Region.of(requireText(properties.getRegion(), "region"));
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(
                requireText(properties.getAccessKey(), "access-key"),
                requireText(properties.getSecretKey(), "secret-key")));
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyle())
                .build();
        ClientOverrideConfiguration overrideConfiguration = ClientOverrideConfiguration.builder()
                .apiCallTimeout(API_CALL_TIMEOUT)
                .apiCallAttemptTimeout(API_ATTEMPT_TIMEOUT)
                .build();

        this.s3Client = S3Client.builder()
                .endpointOverride(internalEndpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .overrideConfiguration(overrideConfiguration)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(publicEndpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .build();
        this.bucket = requireText(properties.getBucket(), "bucket");
        this.uploadUrlTtl = validTtl(properties.getUploadUrlTtl(), "upload-url-ttl");
        this.downloadUrlTtl = validTtl(properties.getDownloadUrlTtl(), "download-url-ttl");
    }

    @Override
    public SignedUpload presignPut(String objectKey, String contentType, long contentLength, String ownerId,
                                   String uploadId) {
        requireText(objectKey, "objectKey");
        requireText(contentType, "contentType");
        if (contentLength <= 0) throw new IllegalArgumentException("contentLength must be positive");
        requireText(ownerId, "ownerId");
        requireText(uploadId, "uploadId");
        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .metadata(Map.of("owner-id", ownerId, "upload-id", uploadId))
                    .build();
            PresignedPutObjectRequest signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(uploadUrlTtl)
                    .putObjectRequest(objectRequest)
                    .build());
            return new SignedUpload(URI.create(signed.url().toString()), requiredClientHeaders(signed),
                    signed.expiration());
        } catch (RuntimeException exception) {
            throw new AttachmentStorageException("presign PUT", exception);
        }
    }

    @Override
    public SignedDownload presignGet(String objectKey) {
        requireText(objectKey, "objectKey");
        try {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();
            PresignedGetObjectRequest signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(downloadUrlTtl)
                    .getObjectRequest(objectRequest)
                    .build());
            return new SignedDownload(URI.create(signed.url().toString()), signed.expiration());
        } catch (RuntimeException exception) {
            throw new AttachmentStorageException("presign GET", exception);
        }
    }

    @Override
    public StoredObjectMetadata head(String objectKey) {
        requireText(objectKey, "objectKey");
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            long contentLength = response.contentLength() == null ? 0 : response.contentLength();
            String contentType = response.contentType() == null ? "application/octet-stream" : response.contentType();
            return new StoredObjectMetadata(contentLength, contentType, response.metadata());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new AttachmentObjectNotFoundException(exception);
            }
            throw new AttachmentStorageException("HEAD", exception);
        } catch (RuntimeException exception) {
            throw new AttachmentStorageException("HEAD", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        requireText(objectKey, "objectKey");
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (RuntimeException exception) {
            throw new AttachmentStorageException("DELETE", exception);
        }
    }

    @PreDestroy
    public void close() {
        presigner.close();
        s3Client.close();
    }

    private Map<String, String> requiredClientHeaders(PresignedRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        request.signedHeaders().forEach((name, values) -> {
            if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length")) {
                headers.put(name, values.stream().collect(Collectors.joining(",")));
            }
        });
        return Map.copyOf(headers);
    }

    private static URI endpoint(String value, String propertyName) {
        try {
            URI endpoint = URI.create(requireText(value, propertyName));
            if (endpoint.getScheme() == null || endpoint.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return endpoint;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("livechat.attachments.s3." + propertyName + " must be a valid URL");
        }
    }

    private static Duration validTtl(Duration value, String propertyName) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("livechat.attachments.s3." + propertyName
                    + " must be positive and no greater than 7 days");
        }
        return value;
    }

    private static String requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("livechat.attachments.s3." + propertyName + " is required");
        }
        return value;
    }
}
