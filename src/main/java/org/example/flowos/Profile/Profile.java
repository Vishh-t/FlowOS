package org.example.flowos.Profile;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.User.User;

import java.time.LocalTime;
import java.util.UUID;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Profile
{
    @Id
    private UUID profileId;

    @OneToOne
    @JoinColumn(name = "userId")
    @MapsId
    private User user;

    private LocalTime wakeTime;

    private LocalTime sleepTime;

    private DayOrNightPersonEnum typeOfPerson;
}
