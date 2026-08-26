package org.example.flowos.Task.Entity;


import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Task.Embedables.TaskTimeRange;
import org.example.flowos.Task.Enums.TaskStatusEnum;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class TaskInstance
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "taskId")
    private Task task;

    private DayOfWeek occurrenceDay;

    @Embedded
    private TaskTimeRange time;

    private TaskStatusEnum status;

    @Nullable
    private LocalDateTime timeOfCompletion;


}
