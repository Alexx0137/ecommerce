package com.ecommerce.modules.user.exception;

import com.ecommerce.common.exception.UnauthenticatedException;

public class InvalidCredentialsException extends UnauthenticatedException {
    
    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }
}