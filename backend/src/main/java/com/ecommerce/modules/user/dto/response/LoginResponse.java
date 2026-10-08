package com.ecommerce.modules.user.dto.response;

public record LoginResponse (
        String accessToken,
        String tokenType,
        long expiresIn
) {}