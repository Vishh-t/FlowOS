package org.example.flowos.Task.Enums;

public enum TaskPriorityEnum
{
    LEAST("PRI_LEAST"),
    LOW("PRI_LOW"),
    MEDIUM("PRI_MEDIUM"),
    HIGH("PRI_HIGH"),
    CRITICAL("PRI_CRITICAL");

    private final String code;
    TaskPriorityEnum(String code) { this.code = code; }
    public String getCode() { return code; }

    public static TaskPriorityEnum fromCode(String code)
    {
        for (var v : values()) if (v.code.equals(code)) return v;
        throw new IllegalArgumentException("Unknown TaskPriorityEnum code: " + code);
    }
}
