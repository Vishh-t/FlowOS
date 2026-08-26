package org.example.flowos.Task.Embedables;

import jakarta.annotation.Nullable;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.flowos.Task.Enums.CommuteApplicationEnum;
import org.example.flowos.Task.Enums.TaskStatusEnum;

import java.time.LocalDateTime;


@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventOccurrence
{
    @Embedded
    private EventTime time;

    private int bufferTimeInMinutes;

    private int commuteTimeInMinutes;

    private CommuteApplicationEnum commuteApplicableWay;


    @Nullable
    private TaskTimeRange preferredTimeRange;


    @Nullable
    private LocalDateTime taskDeadline;

    @Embedded
    private Recurrence taskRecurrence;

}
