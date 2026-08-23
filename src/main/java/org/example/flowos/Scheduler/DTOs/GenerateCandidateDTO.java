package org.example.flowos.Scheduler.DTOs;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.flowos.Profile.Entity.Profile;
import org.example.flowos.Scheduler.Model.WeeklyTimeline;
import org.example.flowos.Task.Entity.Task;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Data
@RequiredArgsConstructor
public class GenerateCandidateDTO
{
    Task task;

    Profile userProfile;

    WeeklyTimeline timeline;

    LocalDateTime now;

    DayOfWeek targetDay;

}
