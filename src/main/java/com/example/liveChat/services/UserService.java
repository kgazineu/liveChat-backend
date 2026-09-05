package com.example.liveChat.services;

import com.example.liveChat.dto.UserLoginDTO;
import com.example.liveChat.dto.UserLoginResponseDTO;
import com.example.liveChat.dto.UserRegisterDTO;
import com.example.liveChat.dto.UserResponseDTO;
import com.example.liveChat.exceptions.UserAlreadyExistsException;
import com.example.liveChat.exceptions.UserNotFoundException;
import com.example.liveChat.infra.security.TokenService;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    public User findUserByIdOrThrow(String userId){
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found "));
    }

    public User loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public User register(UserRegisterDTO data){
        if (userRepository.findByEmail(data.email()).isPresent()) {
            throw new UserAlreadyExistsException("A user with email " + data.email() + " already exists");
        }
        String encryptedPassword = passwordEncoder.encode(data.password());
        var newUser = new User(data.name(), data.email(), encryptedPassword);

        return userRepository.save(newUser);
    }

    public UserLoginResponseDTO login(UserLoginDTO data) {
        var user = userRepository.findByEmail(data.email())
                .orElseThrow(() -> new UserNotFoundException("Invalid email or password"));

        if (!passwordEncoder.matches(data.password(), user.getPassword())) {
            throw new UserNotFoundException("Invalid email or password");
        }

        return tokenService.generateToken(user);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional
    public void deleteUser(String userId) {
        if(!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found");
        }
        userRepository.deleteById(userId);
    }

    public List<UserResponseDTO> searchUsersPartial(String partialEmail) {
        return userRepository.findByEmail(partialEmail)
                .stream()
                .map(UserResponseDTO::forRegister)
                .toList();
    }

    public UserResponseDTO getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Usuário não autenticado");
        }

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
            .orElseThrow(() ->
                new UsernameNotFoundException("User not found with email: " + email)
            );

        return new UserResponseDTO(
            user.getId(),
            user.getName(),
            user.getEmail()
        );
    }

}
