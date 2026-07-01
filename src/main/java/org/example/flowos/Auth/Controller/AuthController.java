package org.example.flowos.Auth.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.flowos.Auth.Dto.GoogleSignInDTO;
import org.example.flowos.Auth.Dto.LogInDTO;
import org.example.flowos.Auth.Dto.RefreshTokenRequestDTO;
import org.example.flowos.Auth.Dto.SignUpDTO;
import org.example.flowos.Auth.Services.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController
{
    private final AuthService service;

    @PostMapping("/signUp")
    public ResponseEntity<?> signUp(@Valid @RequestBody SignUpDTO dto)
    {
        return new ResponseEntity<>(service.signUp(dto), HttpStatus.CREATED);
    }

    @PostMapping("/logIn")
    public ResponseEntity<?> logIn(@Valid @RequestBody LogInDTO dto)
    {
        return new ResponseEntity<>(service.logIn(dto), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequestDTO dto) {
        return new ResponseEntity<>(service.refresh(dto), HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody RefreshTokenRequestDTO dto) {
        service.logout(dto);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/google")
    public ResponseEntity<?> signInWithGoogle(@Valid @RequestBody GoogleSignInDTO dto)
    {
        return new ResponseEntity<>(service.signInWithGoogle(dto), HttpStatus.OK);
    }


}

