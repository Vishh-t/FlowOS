package org.example.flowos.Task.Embedables;

import jakarta.persistence.Embeddable;

import java.time.LocalTime;

@Embeddable
public class TaskTimeRange
{
    private LocalTime taskStartTime;

    private LocalTime taskEndTime;

}
