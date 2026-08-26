package org.example.flowos.Task.Enums;

public enum WeeklyModeEnum
{
    EXACT_DAYS("WMODE_EXACT_DAYS"),
    COUNT_ONLY("WMODE_COUNT_ONLY");

    private final String code;
    WeeklyModeEnum(String code) { this.code = code; }
    public String getCode() { return code; }

    public static WeeklyModeEnum fromCode(String code)
    {
        for (var v : values()) if (v.code.equals(code)) return v;
        throw new IllegalArgumentException("Unknown WeeklyModeEnum code: " + code);
    }
}
