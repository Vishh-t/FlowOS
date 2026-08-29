package org.example.flowos.Task.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FailedOccurrenceDTO
{
    String taskName;
    DayOfWeek day;
}