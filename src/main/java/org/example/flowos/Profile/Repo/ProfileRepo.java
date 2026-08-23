package org.example.flowos.Profile.Repo;

import org.example.flowos.Profile.Entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProfileRepo extends JpaRepository<Profile, UUID>
{
}
