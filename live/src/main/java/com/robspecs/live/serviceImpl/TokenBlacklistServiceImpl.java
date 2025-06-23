package com.robspecs.live.serviceImpl; // Changed package

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.robspecs.live.service.TokenBlacklistService; // Changed import

@Service
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistServiceImpl.class);

    public TokenBlacklistServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        logger.debug("TokenBlacklistServiceImpl initialized");
    }

    @Override
    public void blacklistToken(String token, long expirationInMinutes) {
        logger.info("Blacklisting token: {} with expiration: {} minutes", token, expirationInMinutes);
        redisTemplate.opsForValue().set(token, "BLACKLISTED", expirationInMinutes, TimeUnit.MINUTES);
        logger.debug("Token blacklisted in Redis");
    }

    @Override
    public Boolean isBlacklisted(String token) {
        logger.debug("Checking if token: {} is blacklisted", token);
        String value = redisTemplate.opsForValue().get(token);
        boolean isBlacklisted = "BLACKLISTED".equals(value);
        logger.debug("Token blacklisted status: {}", isBlacklisted);
        return isBlacklisted;
    }
}