package org.example.flowos.Task.Enums;

public enum FlexibilityEnum
{
    FLEXIBLE("FLEX_FLEXIBLE"),
    ANCHORED("FLEX_ANCHORED"),
    FIXED("FLEX_FIXED");

    private final String code;
    FlexibilityEnum(String code) { this.code = code; }
    public String getCode() { return code; }

    public static FlexibilityEnum fromCode(String code)
    {
        for (var v : values()) if (v.code.equals(code)) return v;
        throw new IllegalArgumentException("Unknown FlexibilityEnum code: " + code);
    }
}
