package com.example.liveChat.services;

import com.example.liveChat.dto.CurrentUserResponseDTO;
import com.example.liveChat.dto.PageResponseDTO;
import com.example.liveChat.dto.PaginationRequestDTO;
import com.example.liveChat.dto.UserLoginDTO;
import com.example.liveChat.dto.UserLoginResponseDTO;
import com.example.liveChat.dto.UserRegisterDTO;
import com.example.liveChat.dto.UserResponseDTO;
import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.exceptions.UserAlreadyExistsException;
import com.example.liveChat.exceptions.UserNotFoundException;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.ChannelMessageRepository;
import com.example.liveChat.repositories.FriendshipRepository;
import com.example.liveChat.repositories.PasswordResetTokenRepository;
import com.example.liveChat.repositories.PendingProfileUpdateRepository;
import com.example.liveChat.repositories.RefreshTokenRepository;
import com.example.liveChat.repositories.ServerChannelRepository;
import com.example.liveChat.repositories.ServerInviteRepository;
import com.example.liveChat.repositories.ServerMemberRepository;
import com.example.liveChat.repositories.ServerRepository;
import com.example.liveChat.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;


@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PendingProfileUpdateRepository pendingProfileUpdateRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private ServerMemberRepository serverMemberRepository;

    @Autowired
    private ServerInviteRepository serverInviteRepository;

    @Autowired
    private ServerChannelRepository serverChannelRepository;

    @Autowired
    private ChannelMessageRepository channelMessageRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PasswordResetPasswordPolicy passwordPolicy;

    public User findUserByIdOrThrow(String userId){
        return userRepository.findActiveById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found "));
    }

    public User loadUserByUsername(String email) {
        return userRepository.findActiveByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public User register(UserRegisterDTO data){
        passwordPolicy.validate(data.password());
        String email = normalizeRequiredEmail(data.email());
        if (userRepository.findActiveByEmailIgnoreCase(email).isPresent()) {
            throw new UserAlreadyExistsException("A user with email " + email + " already exists");
        }
        String encryptedPassword = passwordEncoder.encode(data.password());
        var newUser = new User(data.name(), email, encryptedPassword);

        try {
            return userRepository.saveAndFlush(newUser);
        } catch (DataIntegrityViolationException exception) {
            throw new UserAlreadyExistsException("A user with email " + email + " already exists");
        }
    }

    public UserLoginResponseDTO login(UserLoginDTO data) {
        String email = data.email() == null ? "" : data.email().trim().toLowerCase(Locale.ROOT);
        var user = userRepository.findActiveByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(data.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return refreshTokenService.issue(user);
    }

    public PageResponseDTO<UserResponseDTO> findAll(PaginationRequestDTO pagination) {
        var pageable = pagination.toPageable(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        return PageResponseDTO.from(userRepository.findAllActive(pageable).map(UserResponseDTO::from));
    }

    @Transactional
    public void deleteUser(String userId, String loggedUserId) {
        if (!userId.equals(loggedUserId)) {
            throw new AccessDeniedException("You can only delete your own account");
        }

        User user = userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        passwordResetTokenRepository.deleteByUserId(userId);
        pendingProfileUpdateRepository.deleteByUserId(userId);
        refreshTokenRepository.deleteByUserId(userId);
        friendshipRepository.deleteAllInvolvingUser(userId);
        serverInviteRepository.deleteActiveInvolvingUser(userId);

        serverRepository.findOwnedByUserForUpdate(userId).forEach(server ->
                serverMemberRepository
                        .findFirstByServerIdAndUserIdNotAndUserDeletedAtIsNullOrderByJoinedAtAscIdAsc(
                                server.getId(), userId)
                        .ifPresentOrElse(successor -> {
                            server.transferOwnership(successor.getUser());
                            successor.promoteToOwner();
                            serverRepository.save(server);
                            serverMemberRepository.save(successor);
                        }, () -> deleteServerWithoutSuccessor(server.getId())));

        serverMemberRepository.deleteByUserId(userId);
        user.softDelete();
        userRepository.saveAndFlush(user);
        eventPublisher.publishEvent(new AccountDeletedEvent(userId));
    }

    public List<UserResponseDTO> searchUsersPartial(String partialEmail) {
        if (partialEmail == null || partialEmail.isBlank()) return List.of();
        return userRepository.findActiveByEmailIgnoreCase(partialEmail.trim())
                .stream()
                .map(UserResponseDTO::from)
                .toList();
    }

    public CurrentUserResponseDTO getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Usuário não autenticado");
        }

        String email = authentication.getName();

        User user = userRepository.findActiveByEmailIgnoreCase(email)
            .orElseThrow(() ->
                new UsernameNotFoundException("User not found with email: " + email)
            );

        return CurrentUserResponseDTO.from(user);
    }

    private void deleteServerWithoutSuccessor(String serverId) {
        channelMessageRepository.deleteByChannelServerId(serverId);
        serverInviteRepository.deleteByServerId(serverId);
        serverMemberRepository.deleteByServerId(serverId);
        serverChannelRepository.deleteByServerId(serverId);
        serverRepository.deleteById(serverId);
    }

    private String normalizeRequiredEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidRequestException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
