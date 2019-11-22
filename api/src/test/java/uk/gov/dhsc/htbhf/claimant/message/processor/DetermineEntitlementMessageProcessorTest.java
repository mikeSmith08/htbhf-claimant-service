package uk.gov.dhsc.htbhf.claimant.message.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dhsc.htbhf.claimant.eligibility.EligibilityDecisionHandler;
import uk.gov.dhsc.htbhf.claimant.entitlement.PregnancyEntitlementCalculator;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.entity.Message;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycle;
import uk.gov.dhsc.htbhf.claimant.message.MessagePayloadFactory;
import uk.gov.dhsc.htbhf.claimant.message.MessageQueueClient;
import uk.gov.dhsc.htbhf.claimant.message.MessageStatus;
import uk.gov.dhsc.htbhf.claimant.message.MessageType;
import uk.gov.dhsc.htbhf.claimant.message.context.DetermineEntitlementMessageContext;
import uk.gov.dhsc.htbhf.claimant.message.context.MessageContextLoader;
import uk.gov.dhsc.htbhf.claimant.message.payload.MessagePayload;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityAndEntitlementDecision;
import uk.gov.dhsc.htbhf.claimant.service.ClaimMessageSender;
import uk.gov.dhsc.htbhf.claimant.service.EligibilityAndEntitlementService;
import uk.gov.dhsc.htbhf.claimant.service.payments.PaymentCycleService;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.dhsc.htbhf.claimant.message.MessageStatus.COMPLETED;
import static uk.gov.dhsc.htbhf.claimant.message.MessageType.DETERMINE_ENTITLEMENT;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimTestDataFactory.aClaimWithExpectedDeliveryDateAndChildrenDobs;
import static uk.gov.dhsc.htbhf.claimant.testsupport.EligibilityAndEntitlementTestDataFactory.aDecisionWithStatus;
import static uk.gov.dhsc.htbhf.claimant.testsupport.MessageContextTestDataFactory.aDetermineEntitlementMessageContext;
import static uk.gov.dhsc.htbhf.claimant.testsupport.MessageTestDataFactory.aValidMessageWithType;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleTestDataFactory.aPaymentCycleWithClaim;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleTestDataFactory.aPaymentCycleWithStartDateAndClaim;
import static uk.gov.dhsc.htbhf.claimant.testsupport.TestConstants.EXPECTED_DELIVERY_DATE_IN_TWO_MONTHS;
import static uk.gov.dhsc.htbhf.dwp.testhelper.TestConstants.NO_CHILDREN;
import static uk.gov.dhsc.htbhf.dwp.testhelper.TestConstants.ONE_CHILD_UNDER_ONE_AND_ONE_CHILD_BETWEEN_ONE_AND_FOUR;
import static uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus.ELIGIBLE;
import static uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus.INELIGIBLE;

@ExtendWith(MockitoExtension.class)
class DetermineEntitlementMessageProcessorTest {

    @Mock
    private EligibilityAndEntitlementService eligibilityAndEntitlementService;
    @Mock
    private MessageContextLoader messageContextLoader;
    @Mock
    private PaymentCycleService paymentCycleService;
    @Mock
    private MessageQueueClient messageQueueClient;
    @Mock
    private EligibilityDecisionHandler eligibilityDecisionHandler;
    @Mock
    private PregnancyEntitlementCalculator pregnancyEntitlementCalculator;
    @Mock
    private ClaimMessageSender claimMessageSender;

    @InjectMocks
    private DetermineEntitlementMessageProcessor processor;

    @Test
    void shouldSuccessfullyProcessMessageAndTriggerPaymentWhenClaimantIsEligible() {
        //Given
        DetermineEntitlementMessageContext context = buildMessageContext(
                EXPECTED_DELIVERY_DATE_IN_TWO_MONTHS,
                ONE_CHILD_UNDER_ONE_AND_ONE_CHILD_BETWEEN_ONE_AND_FOUR,
                EXPECTED_DELIVERY_DATE_IN_TWO_MONTHS,
                ONE_CHILD_UNDER_ONE_AND_ONE_CHILD_BETWEEN_ONE_AND_FOUR);
        given(messageContextLoader.loadDetermineEntitlementContext(any())).willReturn(context);

        //Eligibility response with children returned
        EligibilityAndEntitlementDecision decision = aDecisionWithStatus(ELIGIBLE);
        given(eligibilityAndEntitlementService.evaluateClaimantForPaymentCycle(any(), any(), any())).willReturn(decision);

        // current payment cycle is not second to last one with vouchers
        given(pregnancyEntitlementCalculator.currentCycleIsSecondToLastCycleWithPregnancyVouchers(any())).willReturn(false);

        //Current payment cycle voucher entitlement mocking
        Message message = aValidMessageWithType(DETERMINE_ENTITLEMENT);

        //When
        MessageStatus messageStatus = processor.processMessage(message);

        //Then
        assertThat(messageStatus).isEqualTo(COMPLETED);
        verify(messageContextLoader).loadDetermineEntitlementContext(message);
        verify(eligibilityAndEntitlementService).evaluateClaimantForPaymentCycle(context.getClaim().getClaimant(),
                context.getCurrentPaymentCycle().getCycleStartDate(),
                context.getPreviousPaymentCycle());

        verify(paymentCycleService).updatePaymentCycle(context.getCurrentPaymentCycle(), decision);
        MessagePayload expectedPayload = MessagePayloadFactory.buildMakePaymentMessagePayload(context.getCurrentPaymentCycle());
        verify(messageQueueClient).sendMessage(expectedPayload, MessageType.MAKE_PAYMENT);
        verify(pregnancyEntitlementCalculator).currentCycleIsSecondToLastCycleWithPregnancyVouchers(context.getCurrentPaymentCycle());
        verifyNoMoreInteractions(eligibilityDecisionHandler, claimMessageSender);
    }

    @Test
    void shouldSendReportABirthReminderEmailWhenClaimantReceivesSecondToLastPregnancyVouchers() {
        //Given
        // The claimant will receive pregnancy vouchers for this cycle and the one after but not after that (given a 12 week grace period and four week cycles).
        LocalDate expectedDeliveryDate = LocalDate.now().minusWeeks(5);
        DetermineEntitlementMessageContext context = buildMessageContext(expectedDeliveryDate, NO_CHILDREN, expectedDeliveryDate, NO_CHILDREN);
        given(messageContextLoader.loadDetermineEntitlementContext(any())).willReturn(context);

        //Eligibility response with no children returned
        EligibilityAndEntitlementDecision decision = aDecisionWithStatus(ELIGIBLE);
        given(eligibilityAndEntitlementService.evaluateClaimantForPaymentCycle(any(), any(), any())).willReturn(decision);

        // current payment cycle is second to last one with vouchers
        given(pregnancyEntitlementCalculator.currentCycleIsSecondToLastCycleWithPregnancyVouchers(any())).willReturn(true);

        //Current payment cycle voucher entitlement mocking
        Message message = aValidMessageWithType(DETERMINE_ENTITLEMENT);

        //When
        MessageStatus messageStatus = processor.processMessage(message);

        //Then
        assertThat(messageStatus).isEqualTo(COMPLETED);
        verify(pregnancyEntitlementCalculator).currentCycleIsSecondToLastCycleWithPregnancyVouchers(context.getCurrentPaymentCycle());
        verify(claimMessageSender).sendReportABirthEmailMessage(context.getClaim());
        verifyNoMoreInteractions(eligibilityDecisionHandler);
    }

    @Test
    void shouldSuccessfullyProcessMessageAndHandleIneligibleDecision() {
        //Given
        DetermineEntitlementMessageContext context = buildMessageContext(
                EXPECTED_DELIVERY_DATE_IN_TWO_MONTHS,
                ONE_CHILD_UNDER_ONE_AND_ONE_CHILD_BETWEEN_ONE_AND_FOUR,
                EXPECTED_DELIVERY_DATE_IN_TWO_MONTHS,
                ONE_CHILD_UNDER_ONE_AND_ONE_CHILD_BETWEEN_ONE_AND_FOUR);
        given(messageContextLoader.loadDetermineEntitlementContext(any())).willReturn(context);

        EligibilityAndEntitlementDecision decision = aDecisionWithStatus(INELIGIBLE);
        given(eligibilityAndEntitlementService.evaluateClaimantForPaymentCycle(any(), any(), any())).willReturn(decision);

        //Current payment cycle voucher entitlement mocking
        Message message = aValidMessageWithType(DETERMINE_ENTITLEMENT);

        //When
        MessageStatus messageStatus = processor.processMessage(message);

        //Then
        assertThat(messageStatus).isEqualTo(COMPLETED);
        verify(messageContextLoader).loadDetermineEntitlementContext(message);
        verify(eligibilityAndEntitlementService).evaluateClaimantForPaymentCycle(context.getClaim().getClaimant(),
                context.getCurrentPaymentCycle().getCycleStartDate(),
                context.getPreviousPaymentCycle());

        verify(paymentCycleService).updatePaymentCycle(context.getCurrentPaymentCycle(), decision);
        verify(eligibilityDecisionHandler)
                .handleIneligibleDecision(context.getClaim(), context.getPreviousPaymentCycle(), context.getCurrentPaymentCycle(), decision);
        verifyNoMoreInteractions(messageQueueClient, pregnancyEntitlementCalculator, claimMessageSender);
    }

    private DetermineEntitlementMessageContext buildMessageContext(LocalDate previousCycleExpectedDeliveryDate,
                                                                   List<LocalDate> previousCycleChildrenDobs,
                                                                   LocalDate currentCycleExpectedDeliveryDate,
                                                                   List<LocalDate> currentCycleChildrenDobs) {
        //Claim - we are creating two claims to model the claim being at different states (differ by expected delivery date) at these points in time,
        //they are effectively the same Claim apart from the UUID being different (also on Claimant).
        Claim claimAtPreviousCycle = aClaimWithExpectedDeliveryDateAndChildrenDobs(previousCycleExpectedDeliveryDate, previousCycleChildrenDobs);
        Claim claimAtCurrentCycle = aClaimWithExpectedDeliveryDateAndChildrenDobs(currentCycleExpectedDeliveryDate, currentCycleChildrenDobs);

        //Current payment cycle
        PaymentCycle currentPaymentCycle = aPaymentCycleWithClaim(claimAtCurrentCycle);

        //Previous payment cycle
        LocalDate previousCycleStartDate = LocalDate.now().minusWeeks(4);
        PaymentCycle previousPaymentCycle = aPaymentCycleWithStartDateAndClaim(previousCycleStartDate, claimAtPreviousCycle);

        return aDetermineEntitlementMessageContext(
                currentPaymentCycle,
                previousPaymentCycle,
                claimAtCurrentCycle);
    }
}
