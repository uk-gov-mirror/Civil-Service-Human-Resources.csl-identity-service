package uk.gov.cabinetoffice.csl.controller.identity;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.gov.cabinetoffice.csl.domain.Identity;
import uk.gov.cabinetoffice.csl.dto.*;
import uk.gov.cabinetoffice.csl.exception.IdentityNotFoundException;
import uk.gov.cabinetoffice.csl.service.IdentityService;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.ResponseEntity.*;

@Slf4j
@AllArgsConstructor
@RestController
public class ListIdentitiesController {

    private final IdentityService identityService;

    @PostMapping("/api/identities/remove-reporting-roles")
    @ResponseBody
    @ResponseStatus(OK)
    public BatchProcessResponse removeAdminAccessFromUsers(@RequestBody @Valid UidList uids) {
        return identityService.removeReportingRoles(uids.getUids());
    }

    @GetMapping("/api/identities")
    public ResponseEntity<List<IdentityDto>> listIdentities() {
        return ok(
                identityService.getAllIdentities()
                .stream()
                .map(IdentityDto::new)
                .collect(toList())
        );
    }

    @GetMapping("/api/identities/map")
    public ResponseEntity<Map<String, IdentityDto>> listIdentitiesAsMap() {
        return ok(
                identityService.getAllNormalisedIdentities()
                .stream()
                .collect(toMap(IdentityDto::getUid, o -> o))
        );
    }

    @PostMapping("/api/identities/map-uids-to-emails")
    public ResponseEntity<Map<String, String>> mapUidsToEmails(@RequestBody UidMapRequest body) {
        return ok(
                identityService.getIdentitiesByUidsAndEmailsNormalised(body)
                        .stream()
                        .collect(toMap(IdentityDto::getUid, IdentityDto::getUsername)));
    }


    @GetMapping(value ="/api/identities/map-for-uids", params = "uids")
    public ResponseEntity<Map<String, IdentityDto>> listIdentitiesAsMapForUids(@RequestParam List<String> uids) {
        return ok(
                identityService.getIdentitiesByUidsNormalised(uids)
                .stream()
                .collect(toMap(IdentityDto::getUid, o -> o)));
    }

    @GetMapping(value = "/api/identities", params = "emailAddress")
    public ResponseEntity<IdentityDto> findByEmailAddress(@RequestParam String emailAddress) {
        Identity identity = identityService.getActiveIdentityForEmail(emailAddress);
        if (identity != null) {
            return ok(new IdentityDto(identity));
        }
        return notFound().build();
    }

    @GetMapping(value = "/api/identities", params = "uid")
    public ResponseEntity<IdentityDto> findByUid(@RequestParam String uid) {
        try {
            Identity identity = identityService.getIdentityForUid(uid);
            return ok(new IdentityDto(identity));
        } catch(IdentityNotFoundException e) {
            return notFound().build();
        } catch (Exception e) {
            return status(INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(value = "/api/identity/agency/{uid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IdentityAgencyToken> findAgencyTokenUidByUid(@PathVariable String uid) {
        log.info("Getting agency token uid for identity with uid " + uid);
        try {
            Identity identity = identityService.getIdentityForUid(uid);
            return ok(new IdentityAgencyToken(identity.getUid(), identity.getAgencyTokenUid()));
        } catch(IdentityNotFoundException e) {
            return notFound().build();
        } catch (Exception e) {
            return status(INTERNAL_SERVER_ERROR).build();
        }
    }
}
