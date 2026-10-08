package com.ecommerce.modules.user.mapper;

import org.mapstruct.Mapper;

import com.ecommerce.modules.user.dto.response.UserResponse;
import com.ecommerce.modules.user.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    
    UserResponse toResponse(User user);  
}
