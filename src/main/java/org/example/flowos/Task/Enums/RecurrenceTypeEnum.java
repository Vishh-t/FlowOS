package org.example.flowos.Task.Enums;

public enum RecurrenceTypeEnum
{
    ONE_OFF("RECUR_ONE_OFF"),
    DAILY("RECUR_DAILY"),
    WEEKLY("RECUR_WEEKLY"),
    MONTHLY("RECUR_MONTHLY"),
    ANNUALLY("RECUR_ANNUALLY");

    private final String code;
    RecurrenceTypeEnum(String code) { this.code = code; }
    public String getCode() { return code; }

    public static RecurrenceTypeEnum fromCode(String code)
    {
        for (var v : values()) if (v.code.equals(code)) return v;
        throw new IllegalArgumentException("Unknown RecurrenceTypeEnum code: " + code);
    }
}
