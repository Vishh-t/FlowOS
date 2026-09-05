package org.example.flowos.Auth.Services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import org.example.flowos.Auth.Config.AuthProvider;
import org.example.flowos.Auth.Dto.*;
import org.example.flowos.Auth.Config.JwtUtil;
import org.example.flowos.Exceptions.AlreadyExistsException;
import org.example.flowos.Exceptions.InvalidCredentialsException;
import org.example.flowos.User.Entity.User;
import org.example.flowos.User.Repo.UserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;


@RequiredArgsConstructor
@Service
public class AuthService
{

    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepo repo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService tokenService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    public LoginAndSignUpResponseDTO signUp(SignUpDTO dto)
    {
        if (repo.existsByEmailId(dto.getEmailId()))
        {
            throw new AlreadyExistsException("Email Id Already exists , try a unique emailId");
        }

        User user = new User();
        user.setName(dto.getName());
        user.setEmailId(dto.getEmailId());
        user.setCreatedAt(Instant.now());
        user.setHashedPassword(encoder.encode(dto.getPassword()));
        user.setAuthProvider(AuthProvider.LOCAL);

        User savedUser = repo.save(user);

        return buildResponseDto(savedUser);

    }

    public LoginAndSignUpResponseDTO logIn(LogInDTO dto)
    {
        User storedUser = repo.findByEmailId(dto.getEmailId());

        boolean userExists = storedUser != null && storedUser.getHashedPassword() != null;
        String hashToCheck = userExists ? storedUser.getHashedPassword() : DUMMY_HASH;

        boolean passwordMatches = encoder.matches(dto.getPassword(), hashToCheck);

        if (!userExists || !passwordMatches)
        {
            throw new InvalidCredentialsException("Invalid Email or Password");
        }

        return buildResponseDto(storedUser);

    }

    private LoginAndSignUpResponseDTO buildResponseDto(User user)
    {
        LoginAndSignUpResponseDTO responseDTO = new LoginAndSignUpResponseDTO();
        String accessToken = jwtUtil.generateAccessToken(user.getUserId());
        String refreshToken = tokenService.generateRefreshToken(user.getUserId());
        responseDTO.setUserID(user.getUserId());
        responseDTO.setName(user.getName());
        responseDTO.setAccessToken(accessToken);
        responseDTO.setRefreshToken(refreshToken);

        return responseDTO;
    }

    public RefreshResponseDTO refresh(RefreshTokenRequestDTO dto)
    {
        UUID userId = tokenService.getUserIdFromRefreshToken(dto.getRefreshToken());
        String accessToken = jwtUtil.generateAccessToken(userId);

        RefreshResponseDTO response = new RefreshResponseDTO();
        response.setAccessToken(accessToken);
        return response;
    }

    public void logout(RefreshTokenRequestDTO dto)
    {
        tokenService.revokeRefreshToken(dto.getRefreshToken());
    }

    public LoginAndSignUpResponseDTO signInWithGoogle(GoogleSignInDTO dto)
    {
        GoogleIdToken idToken;
        try
        {
            idToken = googleIdTokenVerifier.verify(dto.getIdToken());
        } catch (Exception e)
        {
            throw new InvalidCredentialsException("Google token verification failed");
        }

        if (idToken == null)
        {
            throw new InvalidCredentialsException("Invalid Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        if (email == null || !Boolean.TRUE.equals(payload.getEmailVerified()))
        {
            throw new InvalidCredentialsException("Google account email is not verified");
        }

        User user = repo.findByGoogleId(googleId);

        if (user == null)
        {
            user = repo.findByEmailId(email);

            if (user != null)
            {
                // existing LOCAL account, same email -> silent link
                user.setGoogleId(googleId);
                repo.save(user);
            } else
            {
                // brand-new user
                user = new User();
                user.setEmailId(email);
                user.setName(name != null ? name : email);
                user.setGoogleId(googleId);
                user.setAuthProvider(AuthProvider.GOOGLE);
                user.setCreatedAt(Instant.now());
                user = repo.save(user);
            }
        }

        return buildResponseDto(user);
    }
}
