package org.example.flowos.Task.Embedables;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskTimeRange
{
    private LocalTime taskStartTime;

    private LocalTime taskEndTime;

}
