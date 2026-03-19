package uk.gov.cabinetoffice.csl.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.cabinetoffice.csl.validation.annotation.UidMapRequestUidsAndEmails;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@UidMapRequestUidsAndEmails
public class UidMapRequest {

    @Size(max = 1000)
    private List<String> uids;

    @Size(max = 1000)
    private List<String> emails;

}
