package org.example.flowos.Profile.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Profile.Enums.DayOrNightPersonEnum;
import org.example.flowos.User.Entity.User;

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

    // IANA zone id, e.g. "Asia/Kolkata". Stored as String rather than java.time.ZoneId to
    // avoid depending on Hibernate's ZoneId mapping support — converted via ZoneId.of(...)
    // at every point it's actually used (see ScheduleGenerationService, TaskInstanceService).
    private String timezone;

    private DayOrNightPersonEnum typeOfPerson;
}
