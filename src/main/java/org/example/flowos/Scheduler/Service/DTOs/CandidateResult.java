package org.example.flowos.Scheduler.Service.DTOs;

import org.example.flowos.Scheduler.Helpers.TimeAndDayRange;

public record CandidateResult(TimeAndDayRange actualRange, TimeAndDayRange paddedRange)
{
}
