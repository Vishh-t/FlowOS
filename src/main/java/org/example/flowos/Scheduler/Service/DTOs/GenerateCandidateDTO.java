package org.example.flowos.Scheduler.Service.DTOs;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.flowos.Profile.Profile;
import org.example.flowos.Scheduler.Model.WeeklyTimeline;
import org.example.flowos.Task.Embedables.TaskTimeRange;
import org.example.flowos.Task.Entity.Task;

import java.time.DayOfWeek;
import java.util.Set;

@Data
@RequiredArgsConstructor
public class GenerateCandidateDTO
{
    Task task;

    Profile userProfile;

    WeeklyTimeline timeline;
}
