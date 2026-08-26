package org.example.flowos.Task.Enums;

public enum TaskCategoryEnum
{
    ACADEMICS("CAT_ACADEMICS"),
    SKILL_BUILDING("CAT_SKILL_BUILDING"),
    FITNESS("CAT_FITNESS"),
    HEALTH("CAT_HEALTH"),
    SOCIAL("CAT_SOCIAL"),
    LEISURE("CAT_LEISURE"),
    CHORES("CAT_CHORES"),
    CAREER("CAT_CAREER"),
    PERSONAL_GROWTH("CAT_PERSONAL_GROWTH");

    private final String code;
    TaskCategoryEnum(String code) { this.code = code; }
    public String getCode() { return code; }

    public static TaskCategoryEnum fromCode(String code)
    {
        for (var v : values()) if (v.code.equals(code)) return v;
        throw new IllegalArgumentException("Unknown TaskCategoryEnum code: " + code);
    }
}
