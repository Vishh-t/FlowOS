package org.example.flowos.Scheduler.Helpers;

import org.example.flowos.Scheduler.Service.DTOs.GenerateCandidateDTO;

import java.time.LocalTime;

public class GenerateCandidateHelperMethods
{

    public static LocalTime addTolerance(LocalTime paddedCandidateEnd, int durationToleranceMinutes)
    {
        paddedCandidateEnd = paddedCandidateEnd.plusMinutes(durationToleranceMinutes);
        return paddedCandidateEnd;
    }

    public static LocalTime addCommuteTime(GenerateCandidateDTO dto, LocalTime paddedCandidateEnd)
    {
        int commuteTimeInMinutes = dto.getTask().getEvent().getCommuteTimeInMinutes();

        return paddedCandidateEnd.plusMinutes(commuteTimeInMinutes);
    }

    public static LocalTime subtractCommuteTime(GenerateCandidateDTO dto, LocalTime ActualCandidateStart)
    {
        int commuteTimeInMinutes = dto.getTask().getEvent().getCommuteTimeInMinutes();

        return ActualCandidateStart.minusMinutes(commuteTimeInMinutes);

    }

    public static LocalTime addBufferTime(GenerateCandidateDTO dto, LocalTime ActualCandidateEnd)
    {
        int bufferTime = dto.getTask().getEvent().getBufferTimeInMinutes();
        if (bufferTime != 0)
        {
            ActualCandidateEnd = ActualCandidateEnd.plusMinutes(bufferTime);
        }
        return ActualCandidateEnd;
    }
}
