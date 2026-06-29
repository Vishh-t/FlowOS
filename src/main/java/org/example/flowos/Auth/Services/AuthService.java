package org.example.flowos.Auth.Services;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Auth.Config.AuthProvider;
import org.example.flowos.Auth.Dto.*;
import org.example.flowos.Auth.Config.JwtUtil;
import org.example.flowos.Exceptions.AlreadyExistsException;
import org.example.flowos.Exceptions.InvalidCredentialsException;
import org.example.flowos.User.User;
import org.example.flowos.User.UserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;


@RequiredArgsConstructor
@Service
public class AuthService
{
    private final UserRepo repo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService tokenService;

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
        if (storedUser == null || !encoder.matches(dto.getPassword(), storedUser.getHashedPassword()))
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

    public RefreshResponseDTO refresh(RefreshTokenRequestDTO dto) {
        UUID userId = tokenService.getUserIdFromRefreshToken(dto.getRefreshToken());
        String accessToken = jwtUtil.generateAccessToken(userId);

        RefreshResponseDTO response = new RefreshResponseDTO();
        response.setAccessToken(accessToken);
        return response;
    }

    public void logout(RefreshTokenRequestDTO dto) {
        tokenService.revokeRefreshToken(dto.getRefreshToken());
    }
}
