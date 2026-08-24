package org.example.flowos.Scheduler.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.flowos.Profile.Entity.Profile;
import org.example.flowos.Scheduler.Model.WeeklyTimeline;
import org.example.flowos.Task.Entity.Task;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class GenerateCandidateDTO
{
    private Task task;

    private Profile userProfile;

    private WeeklyTimeline timeline;

    private LocalDateTime now;

    private DayOfWeek targetDay;

}
