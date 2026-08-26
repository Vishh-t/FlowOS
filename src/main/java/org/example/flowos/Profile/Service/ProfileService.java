package org.example.flowos.Profile.Service;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Profile.DTO.CreateProfileDTO;
import org.example.flowos.Profile.Entity.Profile;
import org.example.flowos.Profile.Repo.ProfileRepo;
import org.example.flowos.User.Entity.User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileService
{
    private final ProfileRepo profileRepo;

    public Profile createProfile(User user, CreateProfileDTO dto)
    {
        Profile profile = new Profile();
        profile.setUser(user);
        profile.setWakeTime(dto.getWakeTime());
        profile.setSleepTime(dto.getSleepTime());
        profile.setTypeOfPerson(dto.getTypeOfPerson());

        return profileRepo.save(profile);
    }
}