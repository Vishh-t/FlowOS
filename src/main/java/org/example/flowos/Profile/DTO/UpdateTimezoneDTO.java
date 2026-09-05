package org.example.flowos.Profile.DTO;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZoneId;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTimezoneDTO
{
    @NotNull
    private String timezone;

    @AssertTrue(message = "timezone must be a valid IANA zone id, e.g. Asia/Kolkata")
    private boolean isTimezoneValid()
    {
        if (timezone == null)
        {
            return false; // redundant with @NotNull, but avoids NPE if that's ever relaxed
        }
        try
        {
            ZoneId.of(timezone);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
