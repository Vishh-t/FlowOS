package org.example.flowos.Task.Enums;

public enum TaskStatusEnum
{
    PENDING("STATUS_PENDING"),
    IN_PROGRESS("STATUS_IN_PROGRESS"),
    DONE("STATUS_DONE");

    private final String code;
    TaskStatusEnum(String code) { this.code = code; }
    public String getCode() { return code; }

    public static TaskStatusEnum fromCode(String code)
    {
        for (var v : values()) if (v.code.equals(code)) return v;
        throw new IllegalArgumentException("Unknown TaskStatusEnum code: " + code);
    }
}
