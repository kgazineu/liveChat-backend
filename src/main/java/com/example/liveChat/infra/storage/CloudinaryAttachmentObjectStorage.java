package com.example.liveChat.infra.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
@Profile("!test")
@SuppressWarnings("unchecked")
public class CloudinaryAttachmentObjectStorage implements AttachmentObjectStorage {
    private static final String PRIVATE_DELIVERY_TYPE = "private";
    private static final String UPLOAD_METHOD = "POST";
    private static final Duration UPLOAD_SIGNATURE_TTL = Duration.ofHours(1);
    private static final Duration MAX_DOWNLOAD_URL_TTL = Duration.ofHours(1);

    private final Cloudinary cloudinary;
    private final String apiKey;
    private final String apiSecret;
    private final String uploadPreset;
    private final Duration downloadUrlTtl;

    public CloudinaryAttachmentObjectStorage(CloudinaryAttachmentStorageProperties properties) {
        this(properties, configuredCloudinary(properties));
    }

    CloudinaryAttachmentObjectStorage(CloudinaryAttachmentStorageProperties properties, Cloudinary cloudinary) {
        Objects.requireNonNull(properties, "properties is required");
        this.cloudinary = Objects.requireNonNull(cloudinary, "cloudinary is required");
        this.apiKey = requireText(properties.getApiKey(), "api-key");
        this.apiSecret = requireText(properties.getApiSecret(), "api-secret");
        this.uploadPreset = requireText(properties.getUploadPreset(), "upload-preset");
        requireCloudName(properties.getCloudName());
        this.downloadUrlTtl = validDownloadTtl(properties.getDownloadUrlTtl());
    }

    @Override
    public SignedUpload signUpload(String objectKey, String contentType, long contentLength, String ownerId,
                                   String uploadId) {
        requireText(objectKey, "objectKey");
        requireText(contentType, "contentType");
        if (contentLength <= 0) throw new IllegalArgumentException("contentLength must be positive");
        requireText(ownerId, "ownerId");
        requireText(uploadId, "uploadId");

        try {
            Instant issuedAt = Instant.now();
            Map<String, Object> signedParameters = new LinkedHashMap<>();
            signedParameters.put("timestamp", issuedAt.getEpochSecond());
            signedParameters.put("upload_preset", uploadPreset);
            signedParameters.put("public_id", objectKey);
            signedParameters.put("type", PRIVATE_DELIVERY_TYPE);
            signedParameters.put("overwrite", false);
            signedParameters.put("allowed_formats", String.join(",", allowedFormats(contentType)));
            signedParameters.put("context", uploadContext(ownerId, uploadId, contentType, contentLength));
            signedParameters.put("headers", "X-Robots-Tag: noindex");

            String signature = cloudinary.apiSignRequest(signedParameters, apiSecret, 2);
            Map<String, String> formFields = new LinkedHashMap<>();
            signedParameters.forEach((name, value) -> formFields.put(name, value.toString()));
            formFields.put("api_key", apiKey);
            formFields.put("signature", signature);

            String resourceType = resourceType(contentType);
            URI uploadUrl = URI.create(cloudinary.uploader().getUploadUrl(
                    ObjectUtils.asMap("resource_type", resourceType)));
            return new SignedUpload(uploadUrl, UPLOAD_METHOD, formFields,
                    issuedAt.plus(UPLOAD_SIGNATURE_TTL));
        } catch (RuntimeException exception) {
            throw new AttachmentStorageException("sign Cloudinary upload", exception);
        }
    }

    @Override
    public SignedDownload signDownload(String objectKey, String originalName, String contentType) {
        requireText(objectKey, "objectKey");
        requireText(originalName, "originalName");
        requireText(contentType, "contentType");
        try {
            Instant expiresAt = Instant.now().plus(downloadUrlTtl);
            String url = cloudinary.privateDownload(objectKey, downloadFormat(originalName, contentType),
                    ObjectUtils.asMap(
                            "resource_type", resourceType(contentType),
                            "type", PRIVATE_DELIVERY_TYPE,
                            "attachment", false,
                            "expires_at", expiresAt.getEpochSecond()));
            return new SignedDownload(URI.create(url), expiresAt);
        } catch (Exception exception) {
            throw new AttachmentStorageException("sign Cloudinary download", exception);
        }
    }

    @Override
    public StoredObjectMetadata inspect(String objectKey, String contentType) {
        requireText(objectKey, "objectKey");
        requireText(contentType, "contentType");
        try {
            Map<?, ?> response = cloudinary.uploader().explicit(objectKey, ObjectUtils.asMap(
                    "resource_type", resourceType(contentType),
                    "type", PRIVATE_DELIVERY_TYPE,
                    "return_error", true));
            throwForProviderError(response, "inspect Cloudinary asset");

            String publicId = stringValue(response.get("public_id"));
            String actualResourceType = stringValue(response.get("resource_type"));
            String deliveryType = stringValue(response.get("type"));
            String format = stringValue(response.get("format"));
            long bytes = longValue(response.get("bytes"), "bytes");
            Map<String, String> metadata = contextMetadata(response.get("context"));

            boolean expectedIdentity = objectKey.equals(publicId)
                    && PRIVATE_DELIVERY_TYPE.equals(deliveryType)
                    && resourceType(contentType).equals(actualResourceType);
            String actualContentType = expectedIdentity
                    ? detectedContentType(actualResourceType, format, objectKey, response)
                    : "application/octet-stream";
            return new StoredObjectMetadata(bytes, actualContentType, metadata);
        } catch (AttachmentStorageException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new AttachmentStorageException("inspect Cloudinary asset", exception);
        }
    }

    @Override
    public void delete(String objectKey, String contentType) {
        requireText(objectKey, "objectKey");
        requireText(contentType, "contentType");
        try {
            Map<?, ?> response = cloudinary.uploader().destroy(objectKey, ObjectUtils.asMap(
                    "resource_type", resourceType(contentType),
                    "type", PRIVATE_DELIVERY_TYPE,
                    "invalidate", true,
                    "return_error", true));
            int errorCode = providerErrorCode(response);
            if (errorCode == 404) return;
            throwForProviderError(response, "delete Cloudinary asset");
            String result = stringValue(response.get("result"));
            if (!"ok".equals(result) && !"not found".equals(result)) {
                throw new AttachmentStorageException("delete Cloudinary asset: invalid provider response");
            }
        } catch (AttachmentStorageException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new AttachmentStorageException("delete Cloudinary asset", exception);
        }
    }

    private static Cloudinary configuredCloudinary(CloudinaryAttachmentStorageProperties properties) {
        Objects.requireNonNull(properties, "properties is required");
        String cloudName = requireCloudName(properties.getCloudName());
        String apiKey = requireText(properties.getApiKey(), "api-key");
        String apiSecret = requireText(properties.getApiSecret(), "api-secret");
        requireText(properties.getUploadPreset(), "upload-preset");
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true,
                "timeout", 10,
                "signature_algorithm", "SHA256",
                "signature_version", 2));
    }

    private static String resourceType(String contentType) {
        String normalized = requireText(contentType, "contentType").toLowerCase(Locale.ROOT);
        if (normalized.startsWith("image/")) return "image";
        if (normalized.startsWith("video/")) return "video";
        return "raw";
    }

    private static List<String> allowedFormats(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> List.of("jpg", "jpeg");
            case "image/png" -> List.of("png");
            case "image/webp" -> List.of("webp");
            case "image/gif" -> List.of("gif");
            case "video/mp4" -> List.of("mp4");
            case "video/webm" -> List.of("webm");
            case "application/pdf" -> List.of("pdf");
            case "text/plain" -> List.of("txt");
            case "text/csv" -> List.of("csv");
            case "text/markdown" -> List.of("md");
            case "application/json" -> List.of("json");
            case "application/msword" -> List.of("doc");
            case "application/vnd.ms-excel" -> List.of("xls");
            case "application/vnd.ms-powerpoint" -> List.of("ppt");
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> List.of("docx");
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> List.of("xlsx");
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> List.of("pptx");
            case "application/vnd.oasis.opendocument.text" -> List.of("odt");
            case "application/vnd.oasis.opendocument.spreadsheet" -> List.of("ods");
            case "application/vnd.oasis.opendocument.presentation" -> List.of("odp");
            default -> throw new IllegalArgumentException("Unsupported attachment content type");
        };
    }

    private static String detectedContentType(String resourceType, String format, String objectKey,
                                              Map<?, ?> response) {
        String normalizedFormat = format == null || format.isBlank()
                ? extensionOf(objectKey)
                : format.toLowerCase(Locale.ROOT);
        if (("image".equals(resourceType) || "video".equals(resourceType))
                && (!positiveDimension(response.get("width")) || !positiveDimension(response.get("height")))) {
            return "application/octet-stream";
        }
        return switch (resourceType + ":" + normalizedFormat) {
            case "image:jpg", "image:jpeg" -> "image/jpeg";
            case "image:png" -> "image/png";
            case "image:webp" -> "image/webp";
            case "image:gif" -> "image/gif";
            case "video:mp4" -> "video/mp4";
            case "video:webm" -> "video/webm";
            case "raw:pdf" -> "application/pdf";
            case "raw:txt" -> "text/plain";
            case "raw:csv" -> "text/csv";
            case "raw:md" -> "text/markdown";
            case "raw:json" -> "application/json";
            case "raw:doc" -> "application/msword";
            case "raw:xls" -> "application/vnd.ms-excel";
            case "raw:ppt" -> "application/vnd.ms-powerpoint";
            case "raw:docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "raw:xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "raw:pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "raw:odt" -> "application/vnd.oasis.opendocument.text";
            case "raw:ods" -> "application/vnd.oasis.opendocument.spreadsheet";
            case "raw:odp" -> "application/vnd.oasis.opendocument.presentation";
            default -> "application/octet-stream";
        };
    }

    private static boolean positiveDimension(Object value) {
        if (value instanceof Number number) return number.longValue() > 0;
        if (value == null) return false;
        try {
            return Long.parseLong(value.toString()) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String downloadFormat(String originalName, String contentType) {
        if ("image/jpeg".equalsIgnoreCase(contentType)) return "jpg";
        String extension = extensionOf(originalName);
        if (extension.isBlank()) throw new IllegalArgumentException("originalName must have an extension");
        return extension;
    }

    private static String uploadContext(String ownerId, String uploadId, String contentType, long contentLength) {
        return "owner_id=" + ownerId
                + "|upload_id=" + uploadId
                + "|content_type=" + contentType
                + "|declared_size=" + contentLength;
    }

    private static Map<String, String> contextMetadata(Object contextValue) {
        Map<String, String> context = new LinkedHashMap<>();
        if (contextValue instanceof Map<?, ?> contextMap) {
            Object custom = contextMap.get("custom");
            Map<?, ?> values = custom instanceof Map<?, ?> customMap ? customMap : contextMap;
            values.forEach((key, value) -> context.put(key.toString(), value == null ? "" : value.toString()));
        } else if (contextValue != null) {
            for (String pair : contextValue.toString().split("\\|")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) context.put(parts[0], parts[1]);
            }
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        copyContext(context, metadata, "owner_id", "owner-id");
        copyContext(context, metadata, "upload_id", "upload-id");
        copyContext(context, metadata, "content_type", "content-type");
        copyContext(context, metadata, "declared_size", "declared-size");
        return metadata;
    }

    private static void copyContext(Map<String, String> source, Map<String, String> target,
                                    String sourceKey, String targetKey) {
        String value = source.get(sourceKey);
        if (value != null) target.put(targetKey, value);
    }

    private static void throwForProviderError(Map<?, ?> response, String operation) {
        int errorCode = providerErrorCode(response);
        if (errorCode == 0) return;
        if (errorCode == 404) throw new AttachmentObjectNotFoundException();
        if (errorCode > 0) {
            throw new AttachmentStorageException(operation + ": provider returned HTTP " + errorCode);
        }
        throw new AttachmentStorageException(operation + ": provider returned an error");
    }

    private static int providerErrorCode(Map<?, ?> response) {
        if (response == null) return -1;
        Object error = response.get("error");
        if (!(error instanceof Map<?, ?> errorMap)) return 0;
        Object code = errorMap.get("http_code");
        if (code instanceof Number number) return number.intValue();
        if (code != null) {
            try {
                return Integer.parseInt(code.toString());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static long longValue(Object value, String field) {
        if (value instanceof Number number) return number.longValue();
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
                // handled below
            }
        }
        throw new AttachmentStorageException("inspect Cloudinary asset: invalid " + field);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String extensionOf(String value) {
        int separator = value.lastIndexOf('.');
        if (separator <= 0 || separator == value.length() - 1) return "";
        return value.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private static Duration validDownloadTtl(Duration value) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(MAX_DOWNLOAD_URL_TTL) > 0) {
            throw new IllegalArgumentException("livechat.attachments.cloudinary.download-url-ttl must be positive "
                    + "and no greater than one hour");
        }
        return value;
    }

    private static String requireCloudName(String value) {
        String cloudName = requireText(value, "cloud-name");
        if (!cloudName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("livechat.attachments.cloudinary.cloud-name is invalid");
        }
        return cloudName;
    }

    private static String requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("livechat.attachments.cloudinary." + propertyName + " is required");
        }
        return value;
    }
}
