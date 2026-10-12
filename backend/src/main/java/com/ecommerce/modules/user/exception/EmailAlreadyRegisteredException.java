package com.ecommerce.modules.user.exception;

import com.ecommerce.common.exception.ConflictException;

public class EmailAlreadyRegisteredException extends ConflictException {

    public EmailAlreadyRegisteredException() {
        super("El email ya está registrado");
    }
}