package org.example.flowos.Profile.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Profile.Enums.DayOrNightPersonEnum;

import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateProfileDTO
{
    @NotNull
    private LocalTime wakeTime;

    @NotNull
    private LocalTime sleepTime;

    @NotNull
    private DayOrNightPersonEnum typeOfPerson;
}