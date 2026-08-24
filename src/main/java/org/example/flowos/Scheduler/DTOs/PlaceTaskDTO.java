package org.example.flowos.Scheduler.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Profile.Entity.Profile;
import org.example.flowos.Scheduler.Model.WeeklyTimeline;
import org.example.flowos.Task.Entity.Task;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class PlaceTaskDTO
{
    private Task task;
    private Profile profile;
    private WeeklyTimeline timeline;
    private LocalDateTime now;

}
