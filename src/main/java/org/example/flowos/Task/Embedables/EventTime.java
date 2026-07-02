package org.example.flowos.Task.Embedables;

import jakarta.persistence.Embeddable;

@Embeddable
public class EventTime
{
    int taskDurationInMinutes;

    int durationToleranceMinutes;

}
