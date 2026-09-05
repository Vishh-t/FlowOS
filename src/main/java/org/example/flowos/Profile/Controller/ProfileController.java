package org.example.flowos.Profile.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.flowos.Profile.DTO.CreateProfileDTO;
import org.example.flowos.Profile.DTO.UpdateTimezoneDTO;
import org.example.flowos.Profile.Service.ProfileService;
import org.example.flowos.User.Entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/profile")
public class ProfileController
{
    private final ProfileService profileService;

    @PostMapping("/create")
    public ResponseEntity<?> createProfile(@AuthenticationPrincipal User user, @Valid @RequestBody CreateProfileDTO dto)
    {
        return new ResponseEntity<>(profileService.createProfile(user, dto), HttpStatus.CREATED);
    }

    @GetMapping("/get")
    public ResponseEntity<?> getProfileByUser(@AuthenticationPrincipal User user)
    {
        return new ResponseEntity<>(profileService.getProfile(user), HttpStatus.OK);
    }

    @PatchMapping("/timezone")
    public ResponseEntity<?> updateTimezone(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateTimezoneDTO dto)
    {
        return new ResponseEntity<>(profileService.updateTimezone(user, dto), HttpStatus.OK);
    }


}