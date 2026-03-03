package uk.gov.cabinetoffice.csl.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collection;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UidRequest {

    @Size(min=1, max=400)
    @NotNull
    private Collection<String> uids;

}
