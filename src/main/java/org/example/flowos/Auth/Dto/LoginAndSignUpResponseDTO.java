package org.example.flowos.Auth.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginAndSignUpResponseDTO
{

    private String token;
    private UUID userID;
    private String name;

}
