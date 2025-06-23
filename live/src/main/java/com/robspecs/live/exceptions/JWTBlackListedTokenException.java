package com.robspecs.live.exceptions;

import org.springframework.security.core.AuthenticationException;

public class JWTBlackListedTokenException extends AuthenticationException {
    public JWTBlackListedTokenException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public JWTBlackListedTokenException(String msg) {
        super(msg);
    }
}