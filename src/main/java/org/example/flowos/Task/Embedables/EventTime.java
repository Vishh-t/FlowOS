package org.example.flowos.Task.Embedables;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventTime
{
    int taskDurationInMinutes;

    int durationToleranceMinutes;

}
