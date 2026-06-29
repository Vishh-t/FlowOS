package org.example.flowos.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepo extends JpaRepository<User, UUID>
{
    boolean existsByEmailId(String emailId);

    User findByEmailId(String emailId);
}
