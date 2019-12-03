package uk.gov.dhsc.htbhf.claimant;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import uk.gov.dhsc.htbhf.claimant.entitlement.PaymentCycleVoucherEntitlement;
import uk.gov.dhsc.htbhf.claimant.entity.*;
import uk.gov.dhsc.htbhf.claimant.message.payload.EmailType;
import uk.gov.dhsc.htbhf.claimant.model.ClaimResultDTO;
import uk.gov.dhsc.htbhf.claimant.model.ClaimStatus;
import uk.gov.dhsc.htbhf.claimant.model.v2.ClaimDTO;
import uk.gov.dhsc.htbhf.claimant.model.v2.ClaimantDTO;
import uk.gov.service.notify.NotificationClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.dhsc.htbhf.claimant.ClaimantServiceAssertionUtils.buildClaimRequestEntity;
import static uk.gov.dhsc.htbhf.claimant.message.payload.EmailType.CHILD_TURNS_FOUR;
import static uk.gov.dhsc.htbhf.claimant.message.payload.EmailType.CHILD_TURNS_ONE;
import static uk.gov.dhsc.htbhf.claimant.message.payload.EmailType.REGULAR_PAYMENT;
import static uk.gov.dhsc.htbhf.claimant.model.ClaimStatus.ACTIVE;
import static uk.gov.dhsc.htbhf.claimant.model.ClaimStatus.EXPIRED;
import static uk.gov.dhsc.htbhf.claimant.model.ClaimStatus.PENDING_EXPIRY;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimDTOTestDataFactory.aClaimDTOWithClaimant;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimantDTOTestDataFactory.aClaimantDTOWithExpectedDeliveryDate;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleVoucherEntitlementTestDataFactory.aPaymentCycleVoucherEntitlementMatchingChildrenAndPregnancy;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleVoucherEntitlementTestDataFactory.aPaymentCycleVoucherEntitlementWithBackdatedVouchersForYoungestChild;
import static uk.gov.dhsc.htbhf.dwp.testhelper.TestConstants.NO_CHILDREN;

/**
 * Runs a claim through the entire lifecycle, preforming (limited) tests at each payment cycle to confirm the correct
 * amounts have been paid and the correct emails sent.
 */
@SuppressWarnings("PMD.AvoidReassigningParameters")
public class ClaimantLifecycleIntegrationTests extends ScheduledServiceIntegrationTest {

    public static final int CYCLE_DURATION = 28;

    @Autowired
    TestRestTemplate restTemplate;

    /**
     * Run a simple claim through its entire lifecycle and confirm the correct events happen & right emails sent.
     * Each vertical bar is the start of a payment cycle.
     *                                                     | one year
     * |...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|
     * | claim starts
     * ..........| Due date & dob ...................................| child's first birthday
     * ..................| New baby reported to DWP
     * ....................| Backdated vouchers paid
     * ........................................................| email about upcoming changes to payment
     * ........................................................................| email that the card will be cancelled in one week
     */
    @Test
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    void shouldProcessClaimFromPregnancyToChildTurningFour() throws JsonProcessingException, NotificationClientException {
        LocalDate expectedDeliveryDate = LocalDate.now().plusDays(70); // 2.5 cycles before due date

        UUID claimId = applyForHealthyStartAsPregnantWomanWithNoChildren(expectedDeliveryDate);
        assertFirstCyclePaidCorrectly(claimId);

        // run through 4 cycles while pregnant (we pay vouchers for up to 12 weeks after due date
        // - their due date is during the 3rd cycle so the claim would expire during 6th if no birth reported)
        expectedDeliveryDate = progressThroughPaymentCyclesForPregnancy(expectedDeliveryDate, claimId, 4);
        Claim claim = repositoryMediator.loadClaim(claimId);
        assertThatReportABirthReminderEmailWasSent(claim);
        verifyNoMoreInteractions(notificationClient);

        // child was born exactly on due date - one and a half cycles ago - notify in time to get backdated vouchers
        List<LocalDate> childrenDob = ageByOneCycle(asList(expectedDeliveryDate));
        assertPaymentCycleWithBackdatedVouchersForNewChild(claimId, childrenDob);
        verifyNoMoreInteractions(notificationClient);

        // run through another 8 cycles until we near the first birthday
        childrenDob = progressThroughRegularPaymentCycles(claimId, childrenDob, 8);

        // should get notification about child turning one in the next cycle
        childrenDob = progressThroughCycleWithUpcomingBirthday(claimId, childrenDob, CHILD_TURNS_ONE);

        // run through 13 cycles per year until we near the 4th birthday (13 + 13 + 12 = 38)
        childrenDob = progressThroughRegularPaymentCycles(claimId, childrenDob, 38);

        // should get notification about child turning four in the next cycle
        childrenDob = progressThroughCycleWithUpcomingBirthday(claimId, childrenDob, CHILD_TURNS_FOUR);

        // should make a final payment
        childrenDob = progressThroughRegularPaymentCycles(claimId, childrenDob, 1);

        // the claim should have been in ACTIVE status for over 4 years by now (including pregnancy)
        claim = repositoryMediator.loadClaim(claimId);
        assertThat(claim.getClaimStatus()).isEqualTo(ClaimStatus.ACTIVE);
        assertThat(claim.getClaimStatusTimestamp()).isBefore(LocalDateTime.now().minusYears(4));

        // should expire the claim
        LocalDateTime now = LocalDateTime.now();
        ageByOneCycle(childrenDob);
        claim = repositoryMediator.loadClaim(claimId);
        assertThat(claim.getClaimStatus()).isEqualTo(EXPIRED);
        assertThat(claim.getClaimStatusTimestamp()).isAfterOrEqualTo(now);
        assertThat(claim.getCardStatus()).isEqualTo(CardStatus.PENDING_CANCELLATION);
        assertThat(claim.getCardStatusTimestamp()).isAfterOrEqualTo(now);

        // should schedule the card for cancellation after four cycles
        ageByNumberOfCycles(4, childrenDob);
        claim = repositoryMediator.loadClaim(claimId);
        assertThat(claim.getCardStatus()).isEqualTo(CardStatus.SCHEDULED_FOR_CANCELLATION);
        assertThat(claim.getCardStatusTimestamp()).isAfterOrEqualTo(now);

        // invoke schedulers to process send email for card is about to be cancelled
        invokeAllSchedulers();
        assertThatCardIsAboutToBeCancelledEmailWasSent(claim);
    }

    /**
     * Run through the lifecycle of a claim where a claimant becomes pregnant but no children ever appear on the feed.
     *                                                     | one year
     * |...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|
     * | claim starts (due date in 25 weeks)
     * |........................| Due date
     * |...............................| email asking claimant to tell their benefit agency about new child
     * |.......................................| email about coming off the scheme
     * |.......................................................| email that the card will be cancelled in one week
     */
    @Test
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    public void shouldProcessClaimFromPregnancyToComingOffTheSchemeWithNoChildrenFromPregnancy() throws JsonProcessingException, NotificationClientException {
        LocalDate expectedDeliveryDate = LocalDate.now().plusWeeks(25);
        UUID claimId = applyForHealthyStartAsPregnantWomanWithNoChildren(expectedDeliveryDate);
        assertFirstCyclePaidCorrectly(claimId);

        // claimant's due date is in 25 weeks time. After 8 cycles (32 weeks), the claimant will still get pregnancy vouchers but get an email reminding them
        // to contact their benefit agency about a new child.
        expectedDeliveryDate = progressThroughPaymentCyclesForPregnancy(expectedDeliveryDate, claimId, 8);
        Claim claim = repositoryMediator.loadClaim(claimId);
        assertThatReportABirthReminderEmailWasSent(claim);
        verifyNoMoreInteractions(notificationClient);

        // claim should be active for 36 weeks now as nine cycles have passed. This is the final cycle that they are eligible for vouchers
        progressThroughPaymentCyclesForPregnancy(expectedDeliveryDate, claimId, 1);
        verifyNoMoreInteractions(notificationClient);
        claim = repositoryMediator.loadClaim(claimId);
        assertThat(claim.getClaimStatus()).isEqualTo(ClaimStatus.ACTIVE);
        assertThat(claim.getClaimStatusTimestamp()).isBefore(LocalDateTime.now().minusWeeks(36));

        // should expire the claim
        LocalDateTime now = LocalDateTime.now();
        ageByOneCycle();
        claim = repositoryMediator.loadClaim(claimId);
        assertThat(claim.getClaimStatus()).isEqualTo(EXPIRED);
        assertThat(claim.getClaimStatusTimestamp()).isAfterOrEqualTo(now);
        assertThat(claim.getCardStatus()).isEqualTo(CardStatus.PENDING_CANCELLATION);
        assertThat(claim.getCardStatusTimestamp()).isAfterOrEqualTo(now);

        // should schedule the card for cancellation after four cycles
        ageByNumberOfCycles(4);
        claim = repositoryMediator.loadClaim(claimId);
        assertThat(claim.getCardStatus()).isEqualTo(CardStatus.SCHEDULED_FOR_CANCELLATION);
        assertThat(claim.getCardStatusTimestamp()).isAfterOrEqualTo(now);

        // invoke schedulers to process send email for card is about to be cancelled
        invokeAllSchedulers();
        assertThatCardIsAboutToBeCancelledEmailWasSent(claim);
    }

    @Test
    public void shouldCorrectlyActivateANewClaim() throws JsonProcessingException, NotificationClientException {
        ClaimDTO claimDTO = aClaimDTOWithClaimant(aClaimantDTOWithExpectedDeliveryDate(LocalDate.now()));
        ClaimantDTO claimant = claimDTO.getClaimant();
        String cardAccountId = UUID.randomUUID().toString();
        stubExternalServicesForSuccessfulResponses(cardAccountId);
        makeNewClaimRestRequest(claimDTO);

        Claim claim = repositoryMediator.getClaimForNino(claimant.getNino());
        assertThat(claim.getClaimStatus()).isEqualTo(ClaimStatus.NEW);
        assertThat(claim.getCardAccountId()).isNull();

        messageProcessorScheduler.processRequestNewCardMessages();
        claim = repositoryMediator.getClaimForNino(claimant.getNino());
        assertThat(claim.getClaimStatus()).isEqualTo(ClaimStatus.NEW);
        assertThat(claim.getCardAccountId()).isNull();
        assertThat(repositoryMediator.getOptionalPaymentCycleForClaim(claim)).isEmpty();

        messageProcessorScheduler.processCompleteNewCardMessages();
        claim = repositoryMediator.getClaimForNino(claimant.getNino());
        assertThat(claim.getClaimStatus()).isEqualTo(ClaimStatus.ACTIVE);
        assertThat(claim.getCardAccountId()).isEqualTo(cardAccountId);
        Optional<PaymentCycle> paymentCycle = repositoryMediator.getOptionalPaymentCycleForClaim(claim);
        assertThat(paymentCycle).isNotEmpty();
        assertThat(paymentCycle.get().getPaymentCycleStatus()).isEqualTo(PaymentCycleStatus.NEW);
    }

    /**
     * Run through a lifecycle where an active claim becomes ineligible due to not being on a qualifying benefit.
     * The claim will go from active to pending expiry, after 16 weeks the claim will become expired and their card
     * status will be set to {@link CardStatus#SCHEDULED_FOR_CANCELLATION}.
     *                                                     | one year
     * |...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|...|
     * | active claim becomes ineligible (due to not being on qualifying benefit). Claim status set to pending expiry, card status set to pending_cancellation
     * |...............| Claim status set to expired. Card status set to scheduled_for_cancellation
     */
    @Test
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    void shouldProcessClaimWhoBecomesIneligibleDueToNoQualifyingBenefit() throws JsonProcessingException, NotificationClientException {
        LocalDate expectedDeliveryDate = LocalDate.now().plusWeeks(25);
        UUID claimId = applyForHealthyStartAsPregnantWomanWithNoChildren(expectedDeliveryDate);
        assertFirstCyclePaidCorrectly(claimId);

        // ineligible decision moves claim into pending expiry
        wiremockManager.stubIneligibleEligibilityResponse();
        ageByOneCycle();
        Claim claim = repositoryMediator.loadClaim(claimId);
        assertThat(claim.getClaimStatus()).isEqualTo(PENDING_EXPIRY);
        assertThat(claim.getCardStatus()).isEqualTo(CardStatus.PENDING_CANCELLATION);

        LocalDateTime now = LocalDateTime.now();
        // 16 weeks pass
        ageByNumberOfCycles(4);
        claim = repositoryMediator.loadClaim(claimId);
        assertThat(claim.getClaimStatus()).isEqualTo(EXPIRED);
        assertThat(claim.getClaimStatusTimestamp()).isAfterOrEqualTo(now);
        assertThat(claim.getCardStatus()).isEqualTo(CardStatus.SCHEDULED_FOR_CANCELLATION);
        assertThat(claim.getCardStatusTimestamp()).isAfterOrEqualTo(now);

        // invoke schedulers to process send email for card is about to be cancelled
        invokeAllSchedulers();
        assertThatCardIsAboutToBeCancelledEmailWasSent(claim);
    }

    @Test
    void shouldProcessClaimWhichBecomesIneligibleThenEligible() throws JsonProcessingException, NotificationClientException {
        LocalDate expectedDeliveryDate = LocalDate.now().plusWeeks(25);
        UUID claimId = applyForHealthyStartAsPregnantWomanWithNoChildren(expectedDeliveryDate);
        assertFirstCyclePaidCorrectly(claimId);

        // ineligible decision moves claim into pending expiry
        wiremockManager.stubIneligibleEligibilityResponse();
        ageByOneCycle();
        Claim claim = repositoryMediator.loadClaim(claimId);
        assertThat(claim.getClaimStatus()).isEqualTo(PENDING_EXPIRY);
        assertThat(claim.getCardStatus()).isEqualTo(CardStatus.PENDING_CANCELLATION);

        // eligible decision moves claim back to active
        wiremockManager.stubSuccessfulEligibilityResponse(NO_CHILDREN);
        ageByOneWeek();
        claim = repositoryMediator.loadClaim(claimId);
        assertThat(claim.getClaimStatus()).isEqualTo(ACTIVE);
        assertThat(claim.getCardStatus()).isEqualTo(CardStatus.ACTIVE);

        assertPaymentCyclePaidCorrectly(claimId, NO_CHILDREN, EmailType.RESTARTED_PAYMENT);
    }

    private LocalDate progressThroughPaymentCyclesForPregnancy(LocalDate expectedDeliveryDate, UUID claimId, int numCycles)
            throws NotificationClientException, JsonProcessingException {
        for (int i = 0; i < numCycles; i++) {
            resetNotificationClient();
            repositoryMediator.ageDatabaseEntities(CYCLE_DURATION);
            wiremockManager.stubSuccessfulEligibilityResponse(NO_CHILDREN);
            invokeAllSchedulers();
            assertPaymentCyclePaidCorrectly(claimId, NO_CHILDREN, REGULAR_PAYMENT);
        }
        return expectedDeliveryDate.minusDays((long) CYCLE_DURATION * numCycles);
    }

    private List<LocalDate> progressThroughCycleWithUpcomingBirthday(UUID claimId, List<LocalDate> childrenDob, EmailType emailType)
            throws NotificationClientException, JsonProcessingException {
        resetNotificationClient();
        childrenDob = ageByOneCycle(childrenDob);
        PaymentCycle paymentCycle = assertPaymentCyclePaidCorrectly(claimId, childrenDob, REGULAR_PAYMENT);
        assertEmailSent(emailType, paymentCycle);
        verifyNoMoreInteractions(notificationClient);
        return childrenDob;
    }

    private List<LocalDate> progressThroughRegularPaymentCycles(UUID claimId, List<LocalDate> childrenDob, int numCycles) throws NotificationClientException,
            JsonProcessingException {
        for (int i = 0; i < numCycles; i++) {
            resetNotificationClient();
            childrenDob = ageByOneCycle(childrenDob);
            assertPaymentCyclePaidCorrectly(claimId, childrenDob, REGULAR_PAYMENT);
            verifyNoMoreInteractions(notificationClient);
        }
        return childrenDob;
    }

    private UUID applyForHealthyStartAsPregnantWomanWithNoChildren(LocalDate expectedDeliveryDate) throws JsonProcessingException, NotificationClientException {
        ClaimDTO claimDTO = aClaimDTOWithClaimant(aClaimantDTOWithExpectedDeliveryDate(expectedDeliveryDate));
        stubExternalServicesForSuccessfulResponses(UUID.randomUUID().toString());
        makeNewClaimRestRequest(claimDTO);
        invokeAllSchedulers();

        ClaimantDTO claimant = claimDTO.getClaimant();
        Claim claim = repositoryMediator.getClaimForNino(claimant.getNino());
        return claim.getId();
    }

    private void makeNewClaimRestRequest(ClaimDTO claimDTO) {
        restTemplate.exchange(buildClaimRequestEntity(claimDTO), ClaimResultDTO.class);
    }

    private void stubExternalServicesForSuccessfulResponses(String cardAccountId) throws JsonProcessingException, NotificationClientException {
        wiremockManager.stubSuccessfulEligibilityResponse(NO_CHILDREN);
        wiremockManager.stubSuccessfulNewCardResponse(cardAccountId);
        wiremockManager.stubSuccessfulCardBalanceResponse(cardAccountId, 0);
        wiremockManager.stubSuccessfulDepositResponse(cardAccountId);
        stubNotificationEmailResponse();
    }

    private List<LocalDate> ageByOneCycle(List<LocalDate> initialChildrenDobs) throws JsonProcessingException {
        repositoryMediator.ageDatabaseEntities(CYCLE_DURATION);
        List<LocalDate> newDobs = initialChildrenDobs.stream().map(localDate -> localDate.minusDays(CYCLE_DURATION)).collect(Collectors.toList());
        wiremockManager.stubSuccessfulEligibilityResponse(newDobs);
        invokeAllSchedulers();
        return newDobs;
    }

    private void ageByOneCycle() {
        repositoryMediator.ageDatabaseEntities(CYCLE_DURATION);
        invokeAllSchedulers();
    }

    private void ageByOneWeek() {
        repositoryMediator.ageDatabaseEntities(7);
        invokeAllSchedulers();
    }

    private void ageByNumberOfCycles(int numberOfCycles) {
        for (int i = 0;i < numberOfCycles;i++) {
            ageByOneCycle();
        }
    }

    private void ageByNumberOfCycles(int numberOfCycles, List<LocalDate> initialChildrenDobs) throws JsonProcessingException {
        for (int i = 0; i < numberOfCycles; i++) {
            ageByOneCycle(initialChildrenDobs);
        }
    }

    private void assertFirstCyclePaidCorrectly(UUID claimId) throws NotificationClientException {
        Claim claim = repositoryMediator.loadClaim(claimId);
        PaymentCycle paymentCycle = repositoryMediator.getCurrentPaymentCycleForClaim(claim);
        assertThat(claim.getClaimStatus()).isEqualTo(ClaimStatus.ACTIVE);
        assertPaymentHasCorrectAmount(claim, paymentCycle, NO_CHILDREN);
        assertThatNewCardEmailSentCorrectly(claim, paymentCycle);
        verifyNoMoreInteractions(notificationClient);
    }

    private PaymentCycle assertPaymentCyclePaidCorrectly(UUID claimId, List<LocalDate> childrenDob, EmailType emailType) throws NotificationClientException {
        Claim claim = repositoryMediator.loadClaim(claimId);
        assertThat(claim.getClaimStatus()).isEqualTo(ClaimStatus.ACTIVE);
        PaymentCycle paymentCycle = getAndAssertPaymentCycle(claim);
        assertPaymentHasCorrectAmount(claim, paymentCycle, childrenDob);
        assertThatPaymentEmailWasSent(paymentCycle, emailType);
        return paymentCycle;
    }

    private void assertPaymentCycleWithBackdatedVouchersForNewChild(UUID claimId, List<LocalDate> childrenDob) throws NotificationClientException {
        Claim claim = repositoryMediator.loadClaim(claimId);
        assertThat(claim.getClaimStatus()).isEqualTo(ClaimStatus.ACTIVE);
        PaymentCycle paymentCycle = getAndAssertPaymentCycle(claim);
        PaymentCycleVoucherEntitlement expectedEntitlement =
                aPaymentCycleVoucherEntitlementWithBackdatedVouchersForYoungestChild(paymentCycle.getCycleStartDate(), childrenDob);
        assertThat(paymentCycle.getVoucherEntitlement()).isEqualTo(expectedEntitlement);
        assertThat(paymentCycle.getPayments()).isNotEmpty();
        Payment payment = paymentCycle.getPayments().iterator().next();
        assertThat(payment.getPaymentAmountInPence()).isEqualTo(expectedEntitlement.getTotalVoucherValueInPence());
        assertThatNewChildEmailWasSent(paymentCycle);
    }

    private PaymentCycle getAndAssertPaymentCycle(Claim claim) {
        PaymentCycle paymentCycle = repositoryMediator.getCurrentPaymentCycleForClaim(claim);
        assertThat(paymentCycle.getPaymentCycleStatus()).isEqualTo(PaymentCycleStatus.FULL_PAYMENT_MADE);
        assertThat(paymentCycle.getCycleStartDate()).isEqualTo(LocalDate.now());
        assertThat(paymentCycle.getCycleEndDate()).isEqualTo(LocalDate.now().plusDays(27));
        return paymentCycle;
    }

    private void assertPaymentHasCorrectAmount(Claim claim, PaymentCycle paymentCycle, List<LocalDate> childrenDob) {
        Claimant claimant = claim.getClaimant();
        PaymentCycleVoucherEntitlement expectedEntitlement =
                aPaymentCycleVoucherEntitlementMatchingChildrenAndPregnancy(paymentCycle.getCycleStartDate(), childrenDob, claimant.getExpectedDeliveryDate());
        assertThat(paymentCycle.getVoucherEntitlement()).isEqualTo(expectedEntitlement);
        assertThat(paymentCycle.getPayments()).isNotEmpty();
        Payment payment = paymentCycle.getPayments().iterator().next();
        assertThat(payment.getPaymentAmountInPence()).isEqualTo(expectedEntitlement.getTotalVoucherValueInPence());
    }

    private void assertEmailSent(EmailType emailType, PaymentCycle paymentCycle) throws NotificationClientException {
        verify(notificationClient).sendEmail(eq(emailType.getTemplateId()), eq(paymentCycle.getClaim().getClaimant().getEmailAddress()), any(), any(), any());
    }

    private void invokeAllSchedulers() {
        messageProcessorScheduler.processRequestNewCardMessages();
        messageProcessorScheduler.processCompleteNewCardMessages();
        messageProcessorScheduler.processFirstPaymentMessages();
        paymentCycleScheduler.createNewPaymentCycles();
        messageProcessorScheduler.processDetermineEntitlementMessages();
        messageProcessorScheduler.processPaymentMessages();
        messageProcessorScheduler.processSendEmailMessages();
        cardCancellationScheduler.handleCardsPendingCancellation();
    }

    private void resetNotificationClient() throws NotificationClientException {
        Mockito.reset(notificationClient);
        stubNotificationEmailResponse();
    }
}