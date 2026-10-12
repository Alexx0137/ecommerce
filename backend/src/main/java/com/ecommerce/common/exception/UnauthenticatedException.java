package com.ecommerce.common.exception;

public abstract class UnauthenticatedException extends RuntimeException {

    protected UnauthenticatedException(String message) {
        super(message);
    }
}
