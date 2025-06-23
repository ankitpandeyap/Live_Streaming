package com.robspecs.live.service;

public interface TokenBlacklistService {
    void blacklistToken(String token, long expirationInMinutes);
    Boolean isBlacklisted(String token);
}