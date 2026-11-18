package com.ecommerce.modules.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ecommerce.modules.user.dto.request.LoginRequest;
import com.ecommerce.modules.user.dto.response.LoginResponse;
import com.ecommerce.modules.user.dto.request.RegisterRequest;
import com.ecommerce.modules.user.dto.response.UserResponse;
import com.ecommerce.modules.user.entity.Role;
import com.ecommerce.modules.user.entity.User;
import com.ecommerce.modules.user.exception.EmailAlreadyRegisteredException;
import com.ecommerce.modules.user.exception.InvalidCredentialsException;
import com.ecommerce.modules.user.exception.UserNotAvailableException;
import com.ecommerce.modules.user.mapper.UserMapper;
import com.ecommerce.modules.user.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor 
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private String dummyHash;

    @PostConstruct
    void initDummyHash() {
        dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = User.builder()
            .name(request.name())
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .role(Role.CUSTOMER)
            .build();

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }    

    public LoginResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmailAndDeletedAtIsNull(request.email());
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user.get());
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(UserNotAvailableException::new);
        return userMapper.toResponse(user);     
    }
}
