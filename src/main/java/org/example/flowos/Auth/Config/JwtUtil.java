package org.example.flowos.Auth.Config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil
{
    @Value("${jwt.secret}")
    private String secret;

    @Value("${security.jwt.expiration}")
    private int expiryTime;

    private SecretKey getSigningKey()
    {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateAccessToken(UUID userId)
    {

        return Jwts.
                builder().
                subject(userId.toString()).
                issuedAt(new Date()).
                expiration(new Date(System.currentTimeMillis() + expiryTime)).signWith(getSigningKey()).
                compact();
    }

    public UUID extractUserId(String token)
    {
        String id = Jwts.
                parser().
                verifyWith(getSigningKey()).
                build().
                parseSignedClaims(token).
                getPayload().
                getSubject();

        return UUID.fromString(id);
    }

}
