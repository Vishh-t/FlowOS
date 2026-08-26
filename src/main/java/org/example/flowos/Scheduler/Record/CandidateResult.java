package org.example.flowos.Scheduler.Record;

import org.example.flowos.Scheduler.Model.TimeAndDayRange;

public record CandidateResult(TimeAndDayRange actualRange, TimeAndDayRange paddedRange)
{
}
