package uk.gov.dhsc.htbhf.claimant.communications;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dhsc.htbhf.claimant.entitlement.PaymentCycleVoucherEntitlement;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycle;
import uk.gov.dhsc.htbhf.claimant.message.MessageQueueClient;
import uk.gov.dhsc.htbhf.claimant.message.MessageType;
import uk.gov.dhsc.htbhf.claimant.message.payload.EmailMessagePayload;
import uk.gov.dhsc.htbhf.claimant.message.processor.ChildDateOfBirthCalculator;
import uk.gov.dhsc.htbhf.claimant.message.processor.NextPaymentCycleSummary;

import java.time.LocalDate;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static uk.gov.dhsc.htbhf.claimant.message.payload.EmailType.NEW_CHILD_FROM_PREGNANCY;
import static uk.gov.dhsc.htbhf.claimant.message.payload.EmailType.PAYMENT;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimTestDataFactory.aClaimWithExpectedDeliveryDate;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleTestDataFactory.aPaymentCycleWithCycleEntitlementAndClaim;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleTestDataFactory.aValidPaymentCycle;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleVoucherEntitlementTestDataFactory.aPaymentCycleVoucherEntitlementWithBackdatedVouchersForYoungestChild;

@ExtendWith(MockitoExtension.class)
class PaymentCycleNotificationHandlerTest {

    @Mock
    private MessageQueueClient messageQueueClient;
    @Mock
    private UpcomingBirthdayEmailHandler upcomingBirthdayEmailHandler;
    @Mock
    private ChildDateOfBirthCalculator childDateOfBirthCalculator;
    @Mock
    private NextPaymentCycleSummary nextPaymentCycleSummary;
    @Mock
    private EmailMessagePayloadFactory emailMessagePayloadFactory;

    @InjectMocks
    PaymentCycleNotificationHandler paymentCycleNotificationHandler;

    @Test
    public void shouldSendRegularPaymentEmailOnly() {
        PaymentCycle paymentCycle = aValidPaymentCycle();
        given(childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle)).willReturn(nextPaymentCycleSummary);
        given(nextPaymentCycleSummary.hasChildrenTurningFour()).willReturn(false);
        given(nextPaymentCycleSummary.hasChildrenTurningOne()).willReturn(false);
        EmailMessagePayload emailMessagePayload = EmailMessagePayload.builder().build();
        given(emailMessagePayloadFactory.buildEmailMessagePayload(any(), any())).willReturn(emailMessagePayload);

        paymentCycleNotificationHandler.sendNotificationEmails(paymentCycle);

        verifyPaymentEmailNotificationSent(paymentCycle, emailMessagePayload);
        verifyZeroInteractions(upcomingBirthdayEmailHandler);
    }

    @Test
    public void shouldSendNewChildFromPregnancyEmailOnly() {
        Claim claim = aClaimWithExpectedDeliveryDate(LocalDate.now().minusWeeks(8));
        PaymentCycleVoucherEntitlement voucherEntitlement =
                aPaymentCycleVoucherEntitlementWithBackdatedVouchersForYoungestChild(LocalDate.now(), asList(LocalDate.now().minusWeeks(6)));
        PaymentCycle paymentCycle = aPaymentCycleWithCycleEntitlementAndClaim(voucherEntitlement, claim);
        given(childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle)).willReturn(nextPaymentCycleSummary);
        given(nextPaymentCycleSummary.hasChildrenTurningFour()).willReturn(false);
        given(nextPaymentCycleSummary.hasChildrenTurningOne()).willReturn(false);
        EmailMessagePayload emailMessagePayload = EmailMessagePayload.builder().build();
        given(emailMessagePayloadFactory.buildEmailMessagePayload(any(), any())).willReturn(emailMessagePayload);

        paymentCycleNotificationHandler.sendNotificationEmails(paymentCycle);

        verifyNewChildFromPregnancyEmailSent(paymentCycle, emailMessagePayload);
        verifyZeroInteractions(upcomingBirthdayEmailHandler);
    }

    @Test
    public void shouldSendChildTurningOneEmail() {
        PaymentCycle paymentCycle = aValidPaymentCycle();
        given(childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle)).willReturn(nextPaymentCycleSummary);
        given(nextPaymentCycleSummary.hasChildrenTurningFour()).willReturn(false);
        given(nextPaymentCycleSummary.hasChildrenTurningOne()).willReturn(true);

        paymentCycleNotificationHandler.sendNotificationEmails(paymentCycle);

        verify(upcomingBirthdayEmailHandler).sendChildTurnsOneEmail(paymentCycle, nextPaymentCycleSummary);
        verifyNoMoreInteractions(upcomingBirthdayEmailHandler);
    }

    @Test
    public void shouldSendChildTurningFourEmail() {
        PaymentCycle paymentCycle = aValidPaymentCycle();
        given(childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle)).willReturn(nextPaymentCycleSummary);
        given(nextPaymentCycleSummary.hasChildrenTurningFour()).willReturn(true);
        given(nextPaymentCycleSummary.hasChildrenTurningOne()).willReturn(false);

        paymentCycleNotificationHandler.sendNotificationEmails(paymentCycle);

        verify(upcomingBirthdayEmailHandler).sendChildTurnsFourEmail(paymentCycle, nextPaymentCycleSummary);
        verifyNoMoreInteractions(upcomingBirthdayEmailHandler);
    }

    @Test
    public void shouldSendChildTurningOneAndFourEmails() {
        PaymentCycle paymentCycle = aValidPaymentCycle();
        given(childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle)).willReturn(nextPaymentCycleSummary);
        given(nextPaymentCycleSummary.hasChildrenTurningFour()).willReturn(true);
        given(nextPaymentCycleSummary.hasChildrenTurningOne()).willReturn(true);
        EmailMessagePayload emailMessagePayload = EmailMessagePayload.builder().build();
        given(emailMessagePayloadFactory.buildEmailMessagePayload(any(), any())).willReturn(emailMessagePayload);

        paymentCycleNotificationHandler.sendNotificationEmails(paymentCycle);

        verify(upcomingBirthdayEmailHandler).sendChildTurnsOneEmail(paymentCycle, nextPaymentCycleSummary);
        verify(upcomingBirthdayEmailHandler).sendChildTurnsFourEmail(paymentCycle, nextPaymentCycleSummary);
        verifyNoMoreInteractions(upcomingBirthdayEmailHandler);
    }

    private void verifyPaymentEmailNotificationSent(PaymentCycle paymentCycle, EmailMessagePayload emailMessagePayload) {
        verify(messageQueueClient).sendMessage(emailMessagePayload, MessageType.SEND_EMAIL);
        verify(emailMessagePayloadFactory).buildEmailMessagePayload(paymentCycle, PAYMENT);
    }

    private void verifyNewChildFromPregnancyEmailSent(PaymentCycle paymentCycle, EmailMessagePayload emailMessagePayload) {
        verify(messageQueueClient).sendMessage(emailMessagePayload, MessageType.SEND_EMAIL);
        verify(emailMessagePayloadFactory).buildEmailMessagePayload(paymentCycle, NEW_CHILD_FROM_PREGNANCY);
    }
}