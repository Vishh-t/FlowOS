package org.example.flowos.Scheduler.DTOs;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.flowos.Profile.Entity.Profile;
import org.example.flowos.Scheduler.Model.WeeklyTimeline;
import org.example.flowos.Task.Entity.Task;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Data
public class PlaceTaskDTO
{
    Task task ;
    Profile profile;
    WeeklyTimeline timeline;
    LocalDateTime now;

}
