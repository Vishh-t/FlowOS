package org.example.flowos.User;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;
import org.example.flowos.Auth.AuthProvider;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "users")
public class User
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID userId;

    @NonNull
    @Email
    @Column(nullable = false, unique = true)
    private String emailId;

    @Nullable
    private String hashedPassword;

    @NonNull
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    @Nullable
    private String googleId;

    @NonNull
    @Column(nullable = false)
    private Instant createdAt;

    @NonNull
    @Column(nullable = false)
    private String name;

}

