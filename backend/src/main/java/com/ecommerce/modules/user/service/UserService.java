package com.ecommerce.modules.user.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.modules.user.dto.request.RegisterRequest;
import com.ecommerce.modules.user.dto.response.UserResponse;
import com.ecommerce.modules.user.entity.Role;
import com.ecommerce.modules.user.entity.User;
import com.ecommerce.modules.user.mapper.UserMapper;
import com.ecommerce.modules.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor 
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está en registrado");
        }

        User user = User.builder()
            .name(request.name())
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .role(Role.CUSTOMER)
            .build();

        User saved = userRepository.save(user);
        return userMapper.toRespone(saved);
    }    
}
