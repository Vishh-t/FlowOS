package org.example.flowos.Auth.Services;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Exceptions.InvalidCredentialsException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService
{
    private final StringRedisTemplate redisTemplate;

    public String generateRefreshToken(UUID userId)
    {

        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(token, userId.toString(), Duration.ofDays(7));
        return token;

    }

    public UUID getUserIdFromRefreshToken(String token)
    {
        String userId = redisTemplate.opsForValue().get(token);
        if (userId == null)
        {
            throw new InvalidCredentialsException("refresh token is either invalid or expired");
        }
        return UUID.fromString(userId);
    }

    public void revokeRefreshToken(String token)
    {
        redisTemplate.delete(token);
    }




}
