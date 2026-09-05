package org.example.flowos.Profile.Service;

import lombok.RequiredArgsConstructor;
import org.example.flowos.Exceptions.NotFoundException;
import org.example.flowos.Profile.DTO.CreateProfileDTO;
import org.example.flowos.Profile.DTO.UpdateTimezoneDTO;
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
        profile.setTimezone(dto.getTimezone());
        profile.setTypeOfPerson(dto.getTypeOfPerson());

        return profileRepo.save(profile);
    }

    public Profile getProfile(User user)
    {
        return profileRepo.findByUser(user);
    }

    /**
     * Updates only the timezone on an already-existing Profile. Called opportunistically
     * by the client (e.g. on app foreground/login) with the device's current IANA zone id,
     * so the backend's notion of "now" for scheduling/completion-timestamps stays correct
     * without the user ever being asked about timezones directly.
     *
     * Deliberately a partial update (PATCH semantics), not a full-object replace like
     * createProfile — every other field on the Profile is left untouched.
     */
    public Profile updateTimezone(User user, UpdateTimezoneDTO dto)
    {
        Profile profile = profileRepo.findByUser(user);

        if (profile == null)
        {
            throw new NotFoundException("No profile found for user " + user.getUserId());
        }

        if (!dto.getTimezone().equals(profile.getTimezone()))
        {
            profile.setTimezone(dto.getTimezone());
            profile = profileRepo.save(profile);
        }

        return profile;
    }
}