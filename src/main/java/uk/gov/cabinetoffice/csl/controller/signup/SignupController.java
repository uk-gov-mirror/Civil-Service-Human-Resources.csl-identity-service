package uk.gov.cabinetoffice.csl.controller.signup;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.gov.cabinetoffice.csl.domain.Invite;
import uk.gov.cabinetoffice.csl.dto.AgencyToken;
import uk.gov.cabinetoffice.csl.dto.OrganisationalUnit;
import uk.gov.cabinetoffice.csl.exception.ResourceNotFoundException;
import uk.gov.cabinetoffice.csl.exception.UnableToAllocateAgencyTokenException;
import uk.gov.cabinetoffice.csl.service.AgencyTokenCapacityService;
import uk.gov.cabinetoffice.csl.service.CsrsService;
import uk.gov.cabinetoffice.csl.service.IdentityService;
import uk.gov.cabinetoffice.csl.service.InviteService;
import uk.gov.cabinetoffice.csl.util.Utils;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static java.time.LocalDateTime.now;
import static java.time.temporal.ChronoUnit.SECONDS;
import static uk.gov.cabinetoffice.csl.domain.InviteStatus.*;
import static uk.gov.cabinetoffice.csl.util.ApplicationConstants.*;

@Slf4j
@Controller
@RequestMapping("/signup")
public class SignupController {

    private static final String SKIP_MAINTENANCE_PAGE_PARAM_NAME = "username";
    private static final String ENTER_TOKEN_TEMPLATE = "agencytoken/enterToken";
    private static final String CHOOSE_ORGANISATION_TEMPLATE = "signup/chooseOrganisation";
    private static final String REQUEST_INVITE_TEMPLATE = "signup/requestInvite";
    private static final String INVITE_SENT_TEMPLATE = "signup/inviteSent";
    private static final String SIGNUP_TEMPLATE = "signup/signup";
    private static final String SIGNUP_SUCCESS_TEMPLATE = "signup/signupSuccess";
    private static final String PENDING_SIGNUP_TEMPLATE = "signup/pendingSignup";

    private static final String INVITE_MODEL = "invite";
    private static final String ORGANISATIONS_ATTRIBUTE = "organisations";
    private static final String INVITE_CODE_ATTRIBUTE = "inviteCode";
    private static final String TOKEN_INFO_FLASH_ATTRIBUTE = "agencyToken";
    private static final String REQUEST_INVITE_FORM = "requestInviteForm";
    private static final String SIGNUP_FORM = "signupForm";
    private static final String ENTER_TOKEN_FORM = "enterTokenForm";
    private static final String CHOOSE_ORGANISATION_FORM = "chooseOrganisationForm";

    private static final String REDIRECT_SIGNUP = "redirect:/signup/";
    private static final String REDIRECT_SIGNUP_REQUEST = "redirect:/signup/request";
    private static final String REDIRECT_CHOOSE_ORGANISATION = "redirect:/signup/chooseOrganisation/";
    private static final String REDIRECT_ENTER_TOKEN = "redirect:/signup/enterToken/";
    private static final String REDIRECT_INVALID_SIGNUP_CODE = "redirect:/login?error=invalidSignupCode";

    private static final String LPG_UI_URL = "lpgUiUrl";

    @Value("${lpg.uiUrl}")
    private String lpgUiUrl;

    @Value("${invite.validityInSeconds}")
    private int validityInSeconds;

    @Value("${invite.durationAfterReRegAllowedInSeconds}")
    private long durationAfterReRegAllowedInSeconds;

    @Value("${lpg.contactEmail}")
    private String contactEmail;

    @Value("${lpg.contactNumber}")
    private String contactNumber;

    private final InviteService inviteService;

    private final IdentityService identityService;

    private final CsrsService csrsService;

    private final AgencyTokenCapacityService agencyTokenCapacityService;

    private final Utils utils;

    private final Clock clock;

    public SignupController(InviteService inviteService,
                            IdentityService identityService,
                            CsrsService csrsService,
                            AgencyTokenCapacityService agencyTokenCapacityService,
                            Utils utils,
                            Clock clock) {
        this.inviteService = inviteService;
        this.identityService = identityService;
        this.csrsService = csrsService;
        this.agencyTokenCapacityService = agencyTokenCapacityService;
        this.utils = utils;
        this.clock = clock;
    }

    @GetMapping(path = "/request")
    public String requestInvite(Model model) {
        model.addAttribute(REQUEST_INVITE_FORM, new RequestInviteForm());
        return REQUEST_INVITE_TEMPLATE;
    }

    @PostMapping(path = "/request")
    public String sendInvite(Model model,
                             @ModelAttribute @Valid RequestInviteForm form,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {

        model.addAttribute(CONTACT_EMAIL_ATTRIBUTE, contactEmail);
        model.addAttribute(CONTACT_NUMBER_ATTRIBUTE, contactNumber);

        if (bindingResult.hasErrors()) {
            model.addAttribute(REQUEST_INVITE_FORM, form);
            return REQUEST_INVITE_TEMPLATE;
        }

        final String email = form.getEmail();
        log.info("Registration request received for {}", email);
        Optional<Invite> pendingInvite = inviteService.getInviteForEmailAndStatus(email, PENDING);

        if (pendingInvite.isPresent()) {
            Invite invite = pendingInvite.get();
            long durationInSecondsSinceInvited = SECONDS.between(invite.getInvitedAt(), now(clock));
            if (durationInSecondsSinceInvited < durationAfterReRegAllowedInSeconds) {
                log.info("User with email {} is trying to re-register before re-registration allowed time." +
                        " No action is taken. Current pending invite will remain valid until re-registration allowed" +
                        " or until it expires.", email);
                return PENDING_SIGNUP_TEMPLATE;
            } else {
                log.info("User with email {} trying to re-register after re-registration allowed time therefore " +
                        "setting the current pending invite to expired.", email);
                inviteService.updateInviteStatus(invite.getCode(), EXPIRED);
                return REDIRECT_SIGNUP_REQUEST;
            }
        }

        if (identityService.isIdentityExistsForEmail(email)) {
            log.warn("User is trying to sign-up with an email {} which is already in use.", email);
            model.addAttribute("validityDuration",
                    utils.convertSecondsIntoDaysHoursMinutesSeconds(validityInSeconds));
            model.addAttribute("emailId", email);
            return INVITE_SENT_TEMPLATE;
        }

        final String domain = utils.getDomainFromEmailAddress(email);
        if (identityService.isDomainInAnAgencyToken(domain)) {
            log.info("Sending invite to agency user with email {}", email);
            inviteService.sendSelfSignupInvite(email, false);
            model.addAttribute("validityDuration",
                    utils.convertSecondsIntoDaysHoursMinutesSeconds(validityInSeconds));
            model.addAttribute("emailId", email);
            return INVITE_SENT_TEMPLATE;
        }

        if (identityService.isDomainAllowListed(domain)) {
            log.info("Sending invite to allowListed user with email {}", email);
            inviteService.sendSelfSignupInvite(email, true);
            model.addAttribute("validityDuration",
                    utils.convertSecondsIntoDaysHoursMinutesSeconds(validityInSeconds));
            model.addAttribute("emailId", email);
            return INVITE_SENT_TEMPLATE;
        }

        log.info("The domain for email {} is neither allowListed nor part of an Agency token.", email);
        redirectAttributes.addFlashAttribute(STATUS_ATTRIBUTE,
                "Your organisation is unable to use this service. Please contact your line manager.");
        return REDIRECT_SIGNUP_REQUEST;
    }

    @GetMapping("/{code}")
    public String signup(Model model, @PathVariable(value = "code") String code,
                         RedirectAttributes redirectAttributes) {

        if (!inviteService.isInviteCodeExists(code)) {
            log.info("Signup code for invite is not valid. Redirecting to signup page.");
            redirectAttributes.addFlashAttribute(STATUS_ATTRIBUTE,
                    "This registration link is not valid.\n " +
                            "Please check the link and try again.");
            return REDIRECT_SIGNUP_REQUEST;
        }

        if (inviteService.isInviteCodeUsed(code)) {
            log.info("Signup code for invite is already used. Redirecting to signup page.");
            redirectAttributes.addFlashAttribute(STATUS_ATTRIBUTE,
                    "This registration link is already used.\n" +
                            "Please enter your details to create an account.");
            return REDIRECT_SIGNUP_REQUEST;
        }

        if (inviteService.isInviteCodeExpired(code)) {
            log.info("Signup code for invite is expired. Redirecting to signup page.");
            redirectAttributes.addFlashAttribute(STATUS_ATTRIBUTE,
                    "This registration link has now expired.\n" +
                            "Please re-enter your details to create an account.");
            inviteService.updateInviteStatus(code, EXPIRED);
            return REDIRECT_SIGNUP_REQUEST;
        }

        Invite invite = inviteService.getInviteForCode(code);
        if (!invite.isAuthorisedInvite()) {
            log.info("Invited email {} is not authorised yet. Redirecting to choose organisation page.",
                    invite.getForEmail());
            return REDIRECT_CHOOSE_ORGANISATION + code;
        }

        model.addAttribute(INVITE_MODEL, invite);
        model.addAttribute(SIGNUP_FORM, new SignupForm());
        if (model.containsAttribute(TOKEN_INFO_FLASH_ATTRIBUTE)) {
            AgencyToken agencyToken = (AgencyToken) model.asMap().get(TOKEN_INFO_FLASH_ATTRIBUTE);
            model.addAttribute(TOKEN_INFO_FLASH_ATTRIBUTE, agencyToken);
        } else {
            model.addAttribute(TOKEN_INFO_FLASH_ATTRIBUTE, new AgencyToken());
        }

        log.info("Invited email {} is valid and authorised. Redirecting to set password page.",
                invite.getForEmail());
        return SIGNUP_TEMPLATE;
    }

    @PostMapping("/{code}")
    @Transactional(noRollbackFor = {UnableToAllocateAgencyTokenException.class, ResourceNotFoundException.class})
    public String signup(@PathVariable(value = "code") String code,
                         @ModelAttribute @Valid SignupForm signupForm,
                         BindingResult signUpFormBindingResult,
                         @ModelAttribute AgencyToken agencyToken,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        if (signUpFormBindingResult.hasErrors()) {
            model.addAttribute(INVITE_MODEL, inviteService.getInviteForCode(code));
            return SIGNUP_TEMPLATE;
        }

        if (!inviteService.isInviteCodeValid(code)) {
            return REDIRECT_INVALID_SIGNUP_CODE;
        }

        Invite invite = inviteService.getInviteForCode(code);
        if (!invite.isAuthorisedInvite()) {
            log.info("Invited email {} is not authorised yet. Redirecting to enter token page.",
                    invite.getForEmail());
            return REDIRECT_CHOOSE_ORGANISATION + code;
        }

        try {
            log.info("Invite and signup credentials are valid. Creating identity and updating invite to 'Accepted'");
            identityService.createIdentityFromInviteCode(code, signupForm.getPassword(), agencyToken);
        } catch (UnableToAllocateAgencyTokenException e) {
            log.info("UnableToAllocateAgencyTokenException. Redirecting to set password with no spaces error: " + e);
            model.addAttribute(INVITE_MODEL, invite);
            redirectAttributes.addFlashAttribute(STATUS_ATTRIBUTE, SIGNUP_NO_SPACES_AVAILABLE_ERROR_MESSAGE);
            redirectAttributes.addFlashAttribute(TOKEN_INFO_FLASH_ATTRIBUTE, agencyToken);
            return REDIRECT_SIGNUP + code;
        } catch (ResourceNotFoundException e) {
            log.info("ResourceNotFoundException. Redirecting to set password with error: " + e);
            model.addAttribute(INVITE_MODEL, invite);
            redirectAttributes.addFlashAttribute(STATUS_ATTRIBUTE, SIGNUP_RESOURCE_NOT_FOUND_ERROR_MESSAGE);
            return REDIRECT_SIGNUP + code;
        }

        inviteService.updateInviteStatus(code, ACCEPTED);
        model.addAttribute(LPG_UI_URL, lpgUiUrl);
        return SIGNUP_SUCCESS_TEMPLATE;
    }

    @GetMapping(path = "chooseOrganisation/{code}")
    public String enterOrganisation(Model model, @PathVariable(value = "code") String code) {
        Invite invite = inviteService.getValidInviteForCode(code);

        if (invite == null) {
            return REDIRECT_INVALID_SIGNUP_CODE;
        }

        if (invite.isAuthorisedInvite()) {
            return REDIRECT_SIGNUP + code;
        }

        log.info("Invite email {} accessing enter organisation screen for validation", invite.getForEmail());

        final String domain = utils.getDomainFromEmailAddress(invite.getForEmail());
        List<OrganisationalUnit> organisations = csrsService.getOrganisationalUnitsByDomain(domain);

        model.addAttribute(ORGANISATIONS_ATTRIBUTE, organisations);
        model.addAttribute(CHOOSE_ORGANISATION_FORM, new ChooseOrganisationForm());

        return CHOOSE_ORGANISATION_TEMPLATE;
    }

    @PostMapping(path = "chooseOrganisation/{code}")
    public String chooseOrganisation(Model model, @PathVariable(value = "code") String code,
                                     @ModelAttribute @Valid ChooseOrganisationForm form,
                                     BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(CHOOSE_ORGANISATION_FORM, form);
            model.addAttribute(STATUS_ATTRIBUTE, CHOOSE_ORGANISATION_ERROR_MESSAGE);
            return CHOOSE_ORGANISATION_TEMPLATE;
        }

        Invite invite = inviteService.getValidInviteForCode(code);
        if (invite == null) {
            return REDIRECT_INVALID_SIGNUP_CODE;
        }

        if (invite.isAuthorisedInvite()) {
            return REDIRECT_SIGNUP + code;
        }

        String orgCode = form.getOrganisation();

        log.info("Invite email {} selected organisation {}", invite.getForEmail(), orgCode);

        final String domain = utils.getDomainFromEmailAddress(invite.getForEmail());
        List<OrganisationalUnit> organisations = csrsService.getOrganisationalUnitsByDomain(domain);

        Optional<OrganisationalUnit> selectedOrgUnitOptional =
                organisations
                        .stream()
                        .filter(o -> o.getCode().equals(orgCode))
                        .findFirst();

        if (selectedOrgUnitOptional.isEmpty()) {
            model.addAttribute(ORGANISATIONS_ATTRIBUTE, organisations);
            model.addAttribute(CHOOSE_ORGANISATION_FORM, form);
            model.addAttribute(STATUS_ATTRIBUTE, CHOOSE_ORGANISATION_ERROR_MESSAGE);
            return CHOOSE_ORGANISATION_TEMPLATE;
        }

        OrganisationalUnit selectedOrgUnit = selectedOrgUnitOptional.get();
        if (selectedOrgUnit.isDomainAgencyAssigned(domain)) {
            return REDIRECT_ENTER_TOKEN + String.format("%s/%s", code, orgCode);
        }

        if (selectedOrgUnit.isDomainLinked(domain)) {
            inviteService.authoriseAndSaveInvite(invite);
            return REDIRECT_SIGNUP + code;
        }

        return REDIRECT_INVALID_SIGNUP_CODE;
    }

    @GetMapping(path = "enterToken/{code}/{organisationCode}")
    public String enterToken(Model model, @PathVariable(value = "code") String code,
                             @PathVariable(value = "organisationCode") String organisationCode) {
        Invite invite = inviteService.getValidInviteForCode(code);
        if (invite == null) {
            return REDIRECT_INVALID_SIGNUP_CODE;
        }

        if (invite.isAuthorisedInvite()) {
            return REDIRECT_SIGNUP + code;
        }

        final String domain = utils.getDomainFromEmailAddress(invite.getForEmail());
        if (!csrsService.isDomainInAnAgencyTokenWithOrg(domain, organisationCode)) {
            return REDIRECT_CHOOSE_ORGANISATION + code;
        }

        log.info("Invite email {} accessing enter token screen for validation with organisation {}",
                invite.getForEmail(), organisationCode);

        model.addAttribute(ENTER_TOKEN_FORM, new EnterTokenForm());
        model.addAttribute(INVITE_CODE_ATTRIBUTE, code);

        return ENTER_TOKEN_TEMPLATE;
    }

    @PostMapping(path = "enterToken/{code}/{organisationCode}")
    public String chooseToken(Model model, @PathVariable(value = "code") String code,
                              @PathVariable(value = "organisationCode") String orgCode,
                              @ModelAttribute @Valid EnterTokenForm form,
                              BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(CHOOSE_ORGANISATION_FORM, form);
            return CHOOSE_ORGANISATION_TEMPLATE;
        }

        Invite invite = inviteService.getValidInviteForCode(code);
        if (invite == null) {
            return REDIRECT_INVALID_SIGNUP_CODE;
        }

        final String domain = utils.getDomainFromEmailAddress(invite.getForEmail());

        Optional<AgencyToken> agencyTokenOptional = csrsService.getAgencyToken(
                domain, form.getToken(), orgCode);

        if (agencyTokenOptional.isEmpty()) {
            log.info("Token form has failed the validation for domain {}, token {} and organisation {}.",
                    domain, form.getToken(), orgCode);
            redirectAttributes.addFlashAttribute(STATUS_ATTRIBUTE, ENTER_TOKEN_ERROR_MESSAGE);
            return REDIRECT_ENTER_TOKEN + String.format("%s/%s", code, orgCode);
        }

        AgencyToken agencyToken = agencyTokenOptional.get();
        if (!agencyTokenCapacityService.hasSpaceAvailable(agencyToken)) {
            log.info("Agency token uid {} with capacity {} has no spaces available. " +
                            "User with email {} is unable to signup.",
                    agencyToken.getUid(), agencyToken.getCapacity(), invite.getForEmail());
            redirectAttributes.addFlashAttribute(STATUS_ATTRIBUTE, NO_SPACES_AVAILABLE_ERROR_MESSAGE);
            return REDIRECT_ENTER_TOKEN + String.format("%s/%s", code, orgCode);
        }

        inviteService.authoriseAndSaveInvite(invite);
        model.addAttribute(INVITE_MODEL, invite);
        redirectAttributes.addFlashAttribute(TOKEN_INFO_FLASH_ATTRIBUTE, agencyTokenInfo(
                domain, form.getToken(), orgCode));
        log.info("Token form has passed the validation for domain {}, token {} and organisation {}.",
                domain, form.getToken(), orgCode);
        return REDIRECT_SIGNUP + code + "?" + SKIP_MAINTENANCE_PAGE_PARAM_NAME + "=" + invite.getForEmail();
    }

    private AgencyToken agencyTokenInfo(String domain, String token, String org) {
        AgencyToken agencyToken = new AgencyToken();
        agencyToken.setDomain(domain);
        agencyToken.setToken(token);
        agencyToken.setOrg(org);
        return agencyToken;
    }
}
