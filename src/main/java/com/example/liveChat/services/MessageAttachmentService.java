package com.example.liveChat.services;

import com.example.liveChat.dto.AttachmentUploadRequestDTO;
import com.example.liveChat.dto.AttachmentUploadResponseDTO;
import com.example.liveChat.dto.MessageAttachmentResponseDTO;
import com.example.liveChat.exceptions.DirectChannelNotFoundException;
import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.exceptions.ServerChannelNotFoundException;
import com.example.liveChat.exceptions.UserNotFoundException;
import com.example.liveChat.infra.ratelimit.RateLimiter;
import com.example.liveChat.infra.storage.AttachmentObjectStorage;
import com.example.liveChat.infra.storage.SignedDownload;
import com.example.liveChat.infra.storage.SignedUpload;
import com.example.liveChat.infra.storage.StoredObjectMetadata;
import com.example.liveChat.models.ChannelMessage;
import com.example.liveChat.models.ChannelType;
import com.example.liveChat.models.DirectChannel;
import com.example.liveChat.models.DirectMessage;
import com.example.liveChat.models.MessageAttachment;
import com.example.liveChat.models.ServerChannel;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.DirectChannelRepository;
import com.example.liveChat.repositories.MessageAttachmentRepository;
import com.example.liveChat.repositories.ServerChannelRepository;
import com.example.liveChat.repositories.ServerMemberRepository;
import com.example.liveChat.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MessageAttachmentService {
    public static final int MAX_ATTACHMENTS_PER_MESSAGE = 4;
    public static final long MAX_ATTACHMENT_SIZE = 10L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 20_000;
    private static final Duration UPLOAD_RATE_WINDOW = Duration.ofHours(1);
    private static final Duration UPLOAD_BURST_WINDOW = Duration.ofMinutes(1);
    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS_BY_CONTENT_TYPE = Map.ofEntries(
            Map.entry("image/jpeg", Set.of("jpg", "jpeg")),
            Map.entry("image/png", Set.of("png")),
            Map.entry("image/webp", Set.of("webp")),
            Map.entry("image/gif", Set.of("gif")),
            Map.entry("video/mp4", Set.of("mp4")),
            Map.entry("video/webm", Set.of("webm")),
            Map.entry("application/pdf", Set.of("pdf")),
            Map.entry("text/plain", Set.of("txt")),
            Map.entry("text/csv", Set.of("csv")),
            Map.entry("text/markdown", Set.of("md")),
            Map.entry("application/json", Set.of("json")),
            Map.entry("application/msword", Set.of("doc")),
            Map.entry("application/vnd.ms-excel", Set.of("xls")),
            Map.entry("application/vnd.ms-powerpoint", Set.of("ppt")),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx")),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx")),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", Set.of("pptx")),
            Map.entry("application/vnd.oasis.opendocument.text", Set.of("odt")),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet", Set.of("ods")),
            Map.entry("application/vnd.oasis.opendocument.presentation", Set.of("odp")));

    private final MessageAttachmentRepository attachmentRepository;
    private final DirectChannelRepository directChannelRepository;
    private final ServerChannelRepository serverChannelRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final UserRepository userRepository;
    private final AttachmentObjectStorage objectStorage;
    private final RateLimiter rateLimiter;
    private final Duration pendingTtl;

    public MessageAttachmentService(MessageAttachmentRepository attachmentRepository,
                                    DirectChannelRepository directChannelRepository,
                                    ServerChannelRepository serverChannelRepository,
                                    ServerMemberRepository serverMemberRepository,
                                    UserRepository userRepository,
                                    AttachmentObjectStorage objectStorage,
                                    RateLimiter rateLimiter,
                                    @Value("${livechat.attachments.pending-ttl:15m}") Duration pendingTtl) {
        this.attachmentRepository = attachmentRepository;
        this.directChannelRepository = directChannelRepository;
        this.serverChannelRepository = serverChannelRepository;
        this.serverMemberRepository = serverMemberRepository;
        this.userRepository = userRepository;
        this.objectStorage = objectStorage;
        this.rateLimiter = rateLimiter;
        if (pendingTtl == null || pendingTtl.isZero() || pendingTtl.isNegative()) {
            throw new IllegalArgumentException("livechat.attachments.pending-ttl must be positive");
        }
        this.pendingTtl = pendingTtl;
    }

    @Transactional
    public AttachmentUploadResponseDTO reserveForDirectChannel(String channelId, User authenticatedUser,
                                                               AttachmentUploadRequestDTO request) {
        User author = activeUser(authenticatedUser);
        checkUploadRateLimits(author.getId());
        ValidatedUpload upload = validateUpload(request);
        DirectChannel channel = directChannelRepository.findById(channelId)
                .orElseThrow(() -> new DirectChannelNotFoundException("Direct channel not found"));
        if (!channel.hasParticipant(author.getId())) {
            throw new AccessDeniedException("You are not a participant of this direct channel");
        }

        MessageAttachment attachment = MessageAttachment.forDirectChannel(channel, author, newObjectKey(author, upload),
                upload.originalName(), upload.contentType(), upload.size(), upload.width(), upload.height(),
                Instant.now().plus(pendingTtl));
        attachmentRepository.save(attachment);
        return signedUpload(attachment);
    }

    @Transactional
    public AttachmentUploadResponseDTO reserveForServerChannel(String serverId, String channelId,
                                                               User authenticatedUser,
                                                               AttachmentUploadRequestDTO request) {
        User author = activeUser(authenticatedUser);
        checkUploadRateLimits(author.getId());
        ValidatedUpload upload = validateUpload(request);
        ServerChannel channel = serverChannelRepository.findById(channelId)
                .orElseThrow(() -> new ServerChannelNotFoundException("Server channel not found"));
        if (!channel.getServer().getId().equals(serverId)) {
            throw new ServerChannelNotFoundException("Server channel not found");
        }
        if (channel.getType() != ChannelType.TEXT) {
            throw new InvalidRequestException("Attachments can only be sent to text channels");
        }
        if (serverMemberRepository.findByServerIdAndUserId(serverId, author.getId()).isEmpty()) {
            throw new AccessDeniedException("You are not a member of this server");
        }

        MessageAttachment attachment = MessageAttachment.forServerChannel(channel, author, newObjectKey(author, upload),
                upload.originalName(), upload.contentType(), upload.size(), upload.width(), upload.height(),
                Instant.now().plus(pendingTtl));
        attachmentRepository.save(attachment);
        return signedUpload(attachment);
    }

    @Transactional
    public List<MessageAttachment> prepareForDirectMessage(Collection<String> attachmentIds, User author,
                                                           DirectChannel channel) {
        List<MessageAttachment> attachments = lockAndValidateCommon(attachmentIds, author);
        for (MessageAttachment attachment : attachments) {
            if (attachment.getDirectChannel() == null
                    || !attachment.getDirectChannel().getId().equals(channel.getId())) {
                throw new AccessDeniedException("Attachment was not authorized for this direct channel");
            }
            verifyUploadedObject(attachment);
        }
        return attachments;
    }

    @Transactional
    public List<MessageAttachment> prepareForChannelMessage(Collection<String> attachmentIds, User author,
                                                            ServerChannel channel) {
        List<MessageAttachment> attachments = lockAndValidateCommon(attachmentIds, author);
        for (MessageAttachment attachment : attachments) {
            if (attachment.getServerChannel() == null
                    || !attachment.getServerChannel().getId().equals(channel.getId())) {
                throw new AccessDeniedException("Attachment was not authorized for this server channel");
            }
            verifyUploadedObject(attachment);
        }
        return attachments;
    }

    @Transactional
    public List<MessageAttachmentResponseDTO> attachToDirectMessage(List<MessageAttachment> attachments,
                                                                    DirectMessage message) {
        attachments.forEach(attachment -> attachment.attachTo(message));
        attachmentRepository.saveAll(attachments);
        return attachmentResponses(attachments);
    }

    @Transactional
    public List<MessageAttachmentResponseDTO> attachToChannelMessage(List<MessageAttachment> attachments,
                                                                     ChannelMessage message) {
        attachments.forEach(attachment -> attachment.attachTo(message));
        attachmentRepository.saveAll(attachments);
        return attachmentResponses(attachments);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<MessageAttachmentResponseDTO>> responsesForDirectMessages(
            Collection<DirectMessage> messages) {
        List<Long> messageIds = messages.stream().map(DirectMessage::getId).toList();
        if (messageIds.isEmpty()) return Map.of();
        return groupDirect(attachmentRepository.findByDirectMessageIdIn(messageIds));
    }

    @Transactional(readOnly = true)
    public Map<Long, List<MessageAttachmentResponseDTO>> responsesForChannelMessages(
            Collection<ChannelMessage> messages) {
        List<Long> messageIds = messages.stream().map(ChannelMessage::getId).toList();
        if (messageIds.isEmpty()) return Map.of();
        return groupChannel(attachmentRepository.findByChannelMessageIdIn(messageIds));
    }

    private List<MessageAttachment> lockAndValidateCommon(Collection<String> attachmentIds, User author) {
        List<String> ids = normalizedIds(attachmentIds);
        if (ids.isEmpty()) return List.of();
        List<MessageAttachment> found = attachmentRepository.findAllByIdForUpdate(ids);
        if (found.size() != ids.size()) {
            throw new InvalidRequestException("One or more attachments do not exist");
        }
        Map<String, MessageAttachment> byId = new HashMap<>();
        found.forEach(attachment -> byId.put(attachment.getId(), attachment));
        List<MessageAttachment> ordered = ids.stream().map(byId::get).toList();
        Instant now = Instant.now();
        for (MessageAttachment attachment : ordered) {
            if (!attachment.getAuthor().getId().equals(author.getId())) {
                throw new AccessDeniedException("An attachment can only be used by its author");
            }
            if (!attachment.isPending()) {
                throw new InvalidRequestException("Attachment is already associated with a message");
            }
            if (!attachment.getExpiresAt().isAfter(now)) {
                throw new InvalidRequestException("Attachment upload reservation has expired");
            }
        }
        return ordered;
    }

    private void verifyUploadedObject(MessageAttachment attachment) {
        StoredObjectMetadata stored = objectStorage.inspect(attachment.getObjectKey(), attachment.getContentType());
        if (stored.contentLength() != attachment.getSize()) {
            throw new InvalidRequestException("Uploaded attachment size does not match the reservation");
        }
        if (!attachment.getContentType().equalsIgnoreCase(stored.contentType())) {
            throw new InvalidRequestException("Uploaded attachment content type does not match the reservation");
        }
        if (!attachment.getAuthor().getId().equals(stored.metadata().get("owner-id"))
                || !attachment.getId().equals(stored.metadata().get("upload-id"))) {
            throw new InvalidRequestException("Uploaded attachment metadata does not match the reservation");
        }
    }

    private List<String> normalizedIds(Collection<String> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) return List.of();
        if (attachmentIds.size() > MAX_ATTACHMENTS_PER_MESSAGE) {
            throw new InvalidRequestException("A message can have at most " + MAX_ATTACHMENTS_PER_MESSAGE + " attachments");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String id : attachmentIds) {
            if (id == null || id.isBlank()) {
                throw new InvalidRequestException("Attachment id is required");
            }
            unique.add(id.trim());
        }
        if (unique.size() != attachmentIds.size()) {
            throw new InvalidRequestException("Attachment ids must not be repeated");
        }
        return List.copyOf(unique);
    }

    private AttachmentUploadResponseDTO signedUpload(MessageAttachment attachment) {
        SignedUpload signed = objectStorage.signUpload(attachment.getObjectKey(), attachment.getContentType(),
                attachment.getSize(), attachment.getAuthor().getId(), attachment.getId());
        return new AttachmentUploadResponseDTO(attachment.getId(), signed.url(), signed.method(), signed.formFields(),
                signed.expiresAt());
    }

    private List<MessageAttachmentResponseDTO> attachmentResponses(Collection<MessageAttachment> attachments) {
        return attachments.stream()
                .sorted((left, right) -> {
                    int created = left.getCreatedAt().compareTo(right.getCreatedAt());
                    return created != 0 ? created : left.getId().compareTo(right.getId());
                })
                .map(this::attachmentResponse)
                .toList();
    }

    private MessageAttachmentResponseDTO attachmentResponse(MessageAttachment attachment) {
        SignedDownload signed = objectStorage.signDownload(attachment.getObjectKey(), attachment.getOriginalName(),
                attachment.getContentType());
        return new MessageAttachmentResponseDTO(attachment.getId(), attachment.getOriginalName(),
                attachment.getContentType(), attachment.getSize(), attachment.getWidth(), attachment.getHeight(),
                signed.url(), signed.expiresAt());
    }

    private Map<Long, List<MessageAttachmentResponseDTO>> groupDirect(List<MessageAttachment> attachments) {
        Map<Long, List<MessageAttachment>> grouped = new HashMap<>();
        attachments.forEach(attachment -> grouped
                .computeIfAbsent(attachment.getDirectMessage().getId(), ignored -> new ArrayList<>())
                .add(attachment));
        Map<Long, List<MessageAttachmentResponseDTO>> result = new HashMap<>();
        grouped.forEach((messageId, values) -> result.put(messageId, attachmentResponses(values)));
        return result;
    }

    private Map<Long, List<MessageAttachmentResponseDTO>> groupChannel(List<MessageAttachment> attachments) {
        Map<Long, List<MessageAttachment>> grouped = new HashMap<>();
        attachments.forEach(attachment -> grouped
                .computeIfAbsent(attachment.getChannelMessage().getId(), ignored -> new ArrayList<>())
                .add(attachment));
        Map<Long, List<MessageAttachmentResponseDTO>> result = new HashMap<>();
        grouped.forEach((messageId, values) -> result.put(messageId, attachmentResponses(values)));
        return result;
    }

    private ValidatedUpload validateUpload(AttachmentUploadRequestDTO request) {
        if (request == null) throw new InvalidRequestException("Attachment metadata is required");
        String originalName = normalizedOriginalName(request.originalName());
        String contentType = request.contentType() == null ? "" : request.contentType().trim().toLowerCase(Locale.ROOT);
        Set<String> allowedExtensions = ALLOWED_EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
        if (allowedExtensions == null) {
            throw new InvalidRequestException("Unsupported attachment content type");
        }
        String extension = extensionOf(originalName);
        if (!allowedExtensions.contains(extension)) {
            throw new InvalidRequestException("File extension does not match the declared content type");
        }
        if (request.size() == null || request.size() <= 0 || request.size() > MAX_ATTACHMENT_SIZE) {
            throw new InvalidRequestException("Attachment size must be between 1 byte and 10 MB");
        }
        validateDimensions(contentType, request.width(), request.height());
        return new ValidatedUpload(originalName, contentType, request.size(), request.width(), request.height());
    }

    private String normalizedOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank() || originalName.length() > 255) {
            throw new InvalidRequestException("Attachment original name is required and must have at most 255 characters");
        }
        String normalized = originalName.trim();
        if (normalized.contains("/") || normalized.contains("\\")
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidRequestException("Attachment original name is invalid");
        }
        return normalized;
    }

    private String extensionOf(String originalName) {
        int separator = originalName.lastIndexOf('.');
        if (separator <= 0 || separator == originalName.length() - 1) return "";
        return originalName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private void validateDimensions(String contentType, Integer width, Integer height) {
        if (!contentType.startsWith("image/") && (width != null || height != null)) {
            throw new InvalidRequestException("Width and height are only allowed for image attachments");
        }
        if ((width == null) != (height == null)) {
            throw new InvalidRequestException("Attachment width and height must be provided together");
        }
        if (width != null && (width <= 0 || height <= 0
                || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION)) {
            throw new InvalidRequestException("Attachment dimensions are invalid");
        }
    }

    private User activeUser(User authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getId() == null) {
            throw new UserNotFoundException("User not found");
        }
        return userRepository.findActiveById(authenticatedUser.getId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private void checkUploadRateLimits(String userId) {
        rateLimiter.check("attachment-upload-user", userId, 20, UPLOAD_RATE_WINDOW);
        rateLimiter.check("attachment-upload-burst-user", userId, 8, UPLOAD_BURST_WINDOW);
    }

    private String newObjectKey(User author, ValidatedUpload upload) {
        String key = "message-attachments/" + author.getId() + "/" + UUID.randomUUID();
        return upload.contentType().startsWith("image/") || upload.contentType().startsWith("video/")
                ? key
                : key + "." + extensionOf(upload.originalName());
    }

    private record ValidatedUpload(String originalName, String contentType, long size, Integer width, Integer height) {
    }
}
