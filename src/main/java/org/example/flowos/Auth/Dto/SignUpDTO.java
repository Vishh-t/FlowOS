package org.example.flowos.Auth.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignUpDTO
{
    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String emailId;

    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*[^A-Za-z]).{8,16}$",
            message = "Password must be 8-16 characters and contain uppercase, lowercase, and at least one non-alphabet character"
    )
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

}
