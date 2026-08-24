package org.example.flowos.Scheduler.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.example.flowos.Profile.Entity.Profile;
import org.example.flowos.Scheduler.Model.WeeklyTimeline;
import org.example.flowos.Task.Entity.Task;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class PlaceTaskDTO
{
    Task task;
    Profile profile;
    WeeklyTimeline timeline;
    LocalDateTime now;

}
