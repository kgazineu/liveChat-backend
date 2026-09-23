package com.example.liveChat.services;

import com.example.liveChat.dto.UserLoginDTO;
import com.example.liveChat.dto.UserLoginResponseDTO;
import com.example.liveChat.dto.UserRegisterDTO;
import com.example.liveChat.dto.UserResponseDTO;
import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.exceptions.UserAlreadyExistsException;
import com.example.liveChat.exceptions.UserNotFoundException;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.UserRepository;
import com.example.liveChat.repositories.RefreshTokenRepository;
import com.example.liveChat.repositories.PasswordResetTokenRepository;
import com.example.liveChat.repositories.PendingProfileUpdateRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
    private PasswordResetPasswordPolicy passwordPolicy;

    public User findUserByIdOrThrow(String userId){
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found "));
    }

    public User loadUserByUsername(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public User register(UserRegisterDTO data){
        passwordPolicy.validate(data.password());
        String email = normalizeRequiredEmail(data.email());
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
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
        var user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(data.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return refreshTokenService.issue(user);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional
    public void deleteUser(String userId, String loggedUserId) {
        if (!userId.equals(loggedUserId)) {
            throw new AccessDeniedException("You can only delete your own account");
        }
        if(!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found");
        }
        passwordResetTokenRepository.deleteByUserId(userId);
        pendingProfileUpdateRepository.deleteByUserId(userId);
        refreshTokenRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
    }

    public List<UserResponseDTO> searchUsersPartial(String partialEmail) {
        if (partialEmail == null || partialEmail.isBlank()) return List.of();
        return userRepository.findByEmailIgnoreCase(partialEmail.trim())
                .stream()
                .map(UserResponseDTO::forRegister)
                .toList();
    }

    public UserResponseDTO getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Usuário não autenticado");
        }

        String email = authentication.getName();

        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() ->
                new UsernameNotFoundException("User not found with email: " + email)
            );

        return new UserResponseDTO(
            user.getId(),
            user.getName(),
            user.getEmail()
        );
    }

    private String normalizeRequiredEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidRequestException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
