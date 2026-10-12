package com.ecommerce.modules.user.exception;

import com.ecommerce.common.exception.UnauthenticatedException;

public class UserNotAvailableException extends UnauthenticatedException {
    
    public UserNotAvailableException() {
        super("Usuario no disponible");
    }
}