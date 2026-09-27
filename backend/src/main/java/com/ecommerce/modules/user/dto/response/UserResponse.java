package com.ecommerce.modules.user.dto.response;

public record UserResponse (

    Long id,
    String name,
    String email,
    String role
) {}
