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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor 
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(UserNotAvailableException::new);
        return userMapper.toResponse(user);     
    }
}
