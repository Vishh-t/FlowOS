package org.example.flowos.Profile.Enums;

public enum DayOrNightPersonEnum
{
    Morning("DAYNIGHT_MORNING"),
    Night("DAYNIGHT_NIGHT"),
    Neutral("DAYNIGHT_NEUTRAL");

    private final String code;
    DayOrNightPersonEnum(String code) { this.code = code; }
    public String getCode() { return code; }

    public static DayOrNightPersonEnum fromCode(String code)
    {
        for (var v : values()) if (v.code.equals(code)) return v;
        throw new IllegalArgumentException("Unknown DayOrNightPersonEnum code: " + code);
    }
}
