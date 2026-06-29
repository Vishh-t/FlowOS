package org.example.flowos.Auth.Services;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Auth.Config.AuthProvider;
import org.example.flowos.Auth.Dto.LoginAndSignUpResponseDTO;
import org.example.flowos.Auth.Dto.SignUpDTO;
import org.example.flowos.Auth.Dto.LogInDTO;
import org.example.flowos.Auth.Config.JwtUtil;
import org.example.flowos.Exceptions.AlreadyExistsException;
import org.example.flowos.Exceptions.InvalidCredentialsException;
import org.example.flowos.Exceptions.UnauthorizedUserException;
import org.example.flowos.User.User;
import org.example.flowos.User.UserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;


@RequiredArgsConstructor
@Service
public class AuthService
{
    private final UserRepo repo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

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
        String authToken = jwtUtil.generateAccessToken(savedUser.getUserId());

        LoginAndSignUpResponseDTO responseDto = new LoginAndSignUpResponseDTO();
        buildResponseDto(responseDto, savedUser, authToken);

        return responseDto;

    }

    public LoginAndSignUpResponseDTO logIn(LogInDTO dto)
    {
        User storedUser = repo.findByEmailId(dto.getEmailId());
        if (storedUser == null || !encoder.matches(dto.getPassword(), storedUser.getHashedPassword()))
        {
            throw new InvalidCredentialsException("Invalid Email or Password");
        }

        LoginAndSignUpResponseDTO responseDTO = new LoginAndSignUpResponseDTO();
        String authToken = jwtUtil.generateAccessToken(storedUser.getUserId());

        buildResponseDto(responseDTO, storedUser, authToken);
        return responseDTO;

    }

    private static void buildResponseDto(LoginAndSignUpResponseDTO responseDTO, User storedUser, String authToken)
    {
        responseDTO.setUserID(storedUser.getUserId());
        responseDTO.setName(storedUser.getName());
        responseDTO.setToken(authToken);
    }
}
