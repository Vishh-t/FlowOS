package org.example.flowos.Task.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimeRangeDTO
{
    LocalTime taskStartTime;

    LocalTime taskEndTime;
}
