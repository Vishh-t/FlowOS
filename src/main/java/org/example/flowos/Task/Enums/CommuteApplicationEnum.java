package org.example.flowos.Task.Enums;

public enum CommuteApplicationEnum
{
    BEFORE_TASK_ONLY("COMMUTE_BEFORE"),
    AFTER_TASK_ONLY("COMMUTE_AFTER"),
    BOTH_WAYS("COMMUTE_BOTH"),
    NONE("COMMUTE_NONE");

    private final String code;
    CommuteApplicationEnum(String code) { this.code = code; }
    public String getCode() { return code; }

    public static CommuteApplicationEnum fromCode(String code)
    {
        for (var v : values()) if (v.code.equals(code)) return v;
        throw new IllegalArgumentException("Unknown CommuteApplicationEnum code: " + code);
    }
}
