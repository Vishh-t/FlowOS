package org.example.flowos.Scheduler.Helpers;

import org.example.flowos.Task.Entity.Task;
import org.example.flowos.Task.Enums.TaskPriorityEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PriorityInterpreter
{


    private int comparePriority(TaskPriorityEnum task1, TaskPriorityEnum task2)
    {
        // higher priority = earlier in the list, so reverse natural enum order
        return Integer.compare(task2.ordinal(), task1.ordinal());
    }

    public  List<Task> sortForPlacement(List<Task> tasks)
    {
        List<Task> sorted = new ArrayList<>(tasks);

        sorted.sort((a, b) -> {
            int priorityCompare = comparePriority(a.getPriority(), b.getPriority());
            if (priorityCompare != 0)
            {
                return priorityCompare;
            }

            boolean aHasRange = a.getEvent().getPreferredTimeRange() != null;
            boolean bHasRange = b.getEvent().getPreferredTimeRange() != null;

            if (aHasRange && !bHasRange) return -1; // a goes first
            if (!aHasRange && bHasRange) return 1;  // b goes first
            return 0;
        });

        return sorted;
    }
}
