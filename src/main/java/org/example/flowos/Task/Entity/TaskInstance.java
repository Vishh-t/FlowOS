package org.example.flowos.Task.Entity;


import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Task.Embedables.TaskTimeRange;
import org.example.flowos.Task.Enums.TaskStatusEnum;

import java.time.LocalDate;
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

    // Real calendar date this occurrence is placed on — replaces the old DayOfWeek-only
    // occurrenceDay. DayOfWeek is still available on demand via occurrenceDate.getDayOfWeek(),
    // so nothing downstream that only cares about "which day of the week" loses anything —
    // it's derived, not stored twice, so the two can never drift out of sync.
    private LocalDate occurrenceDate;

    @Embedded
    private TaskTimeRange time;

    private TaskStatusEnum status;

    @Nullable
    private LocalDateTime timeOfCompletion;


}
