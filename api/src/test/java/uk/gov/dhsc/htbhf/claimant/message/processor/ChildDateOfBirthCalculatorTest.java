package uk.gov.dhsc.htbhf.claimant.message.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dhsc.htbhf.claimant.entitlement.PaymentCycleEntitlementCalculator;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycle;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static uk.gov.dhsc.htbhf.claimant.message.processor.NextPaymentCycleSummary.NO_CHILDREN;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleTestDataFactory.aPaymentCycleWithPregnancyVouchersOnly;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleTestDataFactory.aValidPaymentCycleBuilder;

@ExtendWith(MockitoExtension.class)
class ChildDateOfBirthCalculatorTest {

    private static final LocalDate YOUNGEST_CHILD_DOB = LocalDate.now().minusMonths(6);
    private static final LocalDate ELDEST_CHILD_DOB = LocalDate.now().minusYears(3).minusMonths(6);
    private static final LocalDate DOB_TURNS_ONE_IN_NEXT_PAYMENT_CYCLE = LocalDate.now().minusYears(1).plusWeeks(5);
    private static final LocalDate DOB_TURNS_FOUR_IN_NEXT_PAYMENT_CYCLE = LocalDate.now().minusYears(4).plusWeeks(5);
    private static final LocalDate CURRENT_CYCLE_FINAL_ENTITLEMENT_DATE = LocalDate.now().plusWeeks(3);
    private static final LocalDate NEXT_CYCLE_FINAL_ENTITLEMENT_DATE = LocalDate.now().plusWeeks(7);

    @Mock
    private PaymentCycleEntitlementCalculator paymentCycleEntitlementCalculator;

    @InjectMocks
    private ChildDateOfBirthCalculator childDateOfBirthCalculator;

    @BeforeEach
    void setupMocks() {
        lenient().when(paymentCycleEntitlementCalculator.getVoucherEntitlementDatesFromStartDate(LocalDate.now()))
                .thenReturn(List.of(
                        LocalDate.now(),
                        LocalDate.now().plusWeeks(1),
                        LocalDate.now().plusWeeks(2),
                        CURRENT_CYCLE_FINAL_ENTITLEMENT_DATE
                ));
        lenient().when(paymentCycleEntitlementCalculator.getVoucherEntitlementDatesFromStartDate(LocalDate.now().plusWeeks(4)))
                .thenReturn(List.of(
                        LocalDate.now().plusWeeks(4),
                        LocalDate.now().plusWeeks(5),
                        LocalDate.now().plusWeeks(6),
                        NEXT_CYCLE_FINAL_ENTITLEMENT_DATE
                ));
    }

    @Test
    void shouldReturnNoChildrenTurningFourInPaymentCycle() {
        //Given standard children DOBs in PaymentCycle are nowhere near triggering in the next PaymentCycle
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                YOUNGEST_CHILD_DOB,
                ELDEST_CHILD_DOB
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        assertThat(summary).isEqualTo(NO_CHILDREN);
    }

    @Test
    void shouldReturnOneChildrenTurningFourThatAffectsNextPayment() {
        //Given eldest child will turn 4 in the next PaymentCycle
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                YOUNGEST_CHILD_DOB,
                DOB_TURNS_FOUR_IN_NEXT_PAYMENT_CYCLE
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        NextPaymentCycleSummary expectedSummary = NextPaymentCycleSummary.builder().numberOfChildrenTurningFour(1).build();
        assertThat(summary).isEqualTo(expectedSummary);
    }

    @Test
    void shouldReturnOneChildrenTurningFourInPaymentCycleWithBirthdayOnEndBoundary() {
        //Given eldest child will turn 4 on the final day of the last entitlement period of the next PaymentCycle.
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                YOUNGEST_CHILD_DOB,
                NEXT_CYCLE_FINAL_ENTITLEMENT_DATE.minusYears(4)
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        NextPaymentCycleSummary expectedSummary = NextPaymentCycleSummary.builder().numberOfChildrenTurningFour(1).build();
        assertThat(summary).isEqualTo(expectedSummary);
    }

    @Test
    void shouldReturnNoChildrenTurningFourInPaymentCycleWithBirthdayAfterEndBoundary() {
        //Given eldest child will turn 4 just after the final day of the last entitlement period of the next PaymentCycle.
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                YOUNGEST_CHILD_DOB,
                NEXT_CYCLE_FINAL_ENTITLEMENT_DATE.minusYears(4).plusDays(1)
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        assertThat(summary).isEqualTo(NO_CHILDREN);
    }

    @Test
    void shouldReturnNoChildrenTurningFourInPaymentCycleWithBirthdayOnStartBoundary() {
        //Given eldest child will turn 4 on the start of the last entitlement date for the current PaymentCycle
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                YOUNGEST_CHILD_DOB,
                CURRENT_CYCLE_FINAL_ENTITLEMENT_DATE.minusYears(4)
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        assertThat(summary).isEqualTo(NO_CHILDREN);
    }

    @Test
    void shouldReturnOneChildrenTurningFourInPaymentCycleWithBirthdayJustAfterStartBoundary() {
        //Given eldest child will turn 4 the day after the start of the last entitlement date for the current PaymentCycle
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                YOUNGEST_CHILD_DOB,
                CURRENT_CYCLE_FINAL_ENTITLEMENT_DATE.minusYears(4).plusDays(1)
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        NextPaymentCycleSummary expectedSummary = NextPaymentCycleSummary.builder().numberOfChildrenTurningFour(1).build();
        assertThat(summary).isEqualTo(expectedSummary);
    }

    @Test
    void shouldReturnTwoChildrenTurningFourInPaymentCycle() {
        //Given twins will turn 4 in the next PaymentCycle
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                YOUNGEST_CHILD_DOB,
                DOB_TURNS_FOUR_IN_NEXT_PAYMENT_CYCLE,
                DOB_TURNS_FOUR_IN_NEXT_PAYMENT_CYCLE
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        NextPaymentCycleSummary expectedSummary = NextPaymentCycleSummary.builder().numberOfChildrenTurningFour(2).build();
        assertThat(summary).isEqualTo(expectedSummary);
    }

    @Test
    void shouldReturnNoChildrenTurningFourInPaymentCycleForPregnantWomanWithNoChildren() {
        //Given a PaymentCycle with no children dobs in it
        PaymentCycle paymentCycle = aPaymentCycleWithPregnancyVouchersOnly(LocalDate.now(), LocalDate.now().plusWeeks(4));
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        assertThat(summary).isEqualTo(NO_CHILDREN);
    }

    @Test
    void shouldReturnNoChildrenTurningOneInPaymentCycle() {
        //Given standard children DOBs in PaymentCycle are nowhere near triggering in the next PaymentCycle
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                YOUNGEST_CHILD_DOB,
                ELDEST_CHILD_DOB
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        assertThat(summary).isEqualTo(NO_CHILDREN);
    }

    @Test
    void shouldReturnOneChildrenTurningOneThatAffectsNextPayment() {
        //Given youngest child will turn 1 in the next PaymentCycle
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                DOB_TURNS_ONE_IN_NEXT_PAYMENT_CYCLE,
                ELDEST_CHILD_DOB
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        NextPaymentCycleSummary expectedSummary = NextPaymentCycleSummary.builder().numberOfChildrenTurningOne(1).build();
        assertThat(summary).isEqualTo(expectedSummary);
    }

    @Test
    void shouldReturnOneChildrenTurningOneInPaymentCycleWithBirthdayOnEndBoundary() {
        //Given youngest child will turn 1 on the final day of the last entitlement period of the next PaymentCycle.
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                NEXT_CYCLE_FINAL_ENTITLEMENT_DATE.minusYears(1),
                ELDEST_CHILD_DOB
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        NextPaymentCycleSummary expectedSummary = NextPaymentCycleSummary.builder().numberOfChildrenTurningOne(1).build();
        assertThat(summary).isEqualTo(expectedSummary);
    }

    @Test
    void shouldReturnNoChildrenTurningOneInPaymentCycleWithBirthdayAfterEndBoundary() {
        //Given youngest child will turn 1 just after the final day of the last entitlement period of the next PaymentCycle.
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                NEXT_CYCLE_FINAL_ENTITLEMENT_DATE.minusYears(1).plusDays(1),
                ELDEST_CHILD_DOB
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        assertThat(summary).isEqualTo(NO_CHILDREN);
    }

    @Test
    void shouldReturnNoChildrenTurningOneInPaymentCycleWithBirthdayOnStartBoundary() {
        //Given youngest child will turn 1 on the start of the last entitlement date for the current PaymentCycle
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                CURRENT_CYCLE_FINAL_ENTITLEMENT_DATE.minusYears(1),
                ELDEST_CHILD_DOB
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        assertThat(summary).isEqualTo(NO_CHILDREN);
    }

    @Test
    void shouldReturnOneChildrenTurningOneInPaymentCycleWithBirthdayJustAfterStartBoundary() {
        //Given youngest child will turn 1 the day after the start of the last entitlement date for the current PaymentCycle
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                CURRENT_CYCLE_FINAL_ENTITLEMENT_DATE.minusYears(1).plusDays(1),
                ELDEST_CHILD_DOB
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        NextPaymentCycleSummary expectedSummary = NextPaymentCycleSummary.builder().numberOfChildrenTurningOne(1).build();
        assertThat(summary).isEqualTo(expectedSummary);
    }

    @Test
    void shouldReturnTwoChildrenTurningOneInPaymentCycle() {
        //Given twins will turn 1 in the next PaymentCycle
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                DOB_TURNS_ONE_IN_NEXT_PAYMENT_CYCLE,
                DOB_TURNS_ONE_IN_NEXT_PAYMENT_CYCLE,
                ELDEST_CHILD_DOB
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        NextPaymentCycleSummary expectedSummary = NextPaymentCycleSummary.builder().numberOfChildrenTurningOne(2).build();
        assertThat(summary).isEqualTo(expectedSummary);
    }

    @Test
    void shouldReturnNoChildrenTurningOneInPaymentCycleForPregnantWomanWithNoChildren() {
        //Given a PaymentCycle with no children dobs in it
        PaymentCycle paymentCycle = aPaymentCycleWithPregnancyVouchersOnly(LocalDate.now(), LocalDate.now().plusWeeks(4));
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        assertThat(summary).isEqualTo(NO_CHILDREN);
    }

    @Test
    void shouldReturnChildrenTurningOneAndChildTurningFourInPaymentCycle() {
        //Given one child will turn 1 and another will turn 4 in the next PaymentCycle
        PaymentCycle paymentCycle = buildPaymentCycleWithChildDobs(
                DOB_TURNS_ONE_IN_NEXT_PAYMENT_CYCLE,
                DOB_TURNS_FOUR_IN_NEXT_PAYMENT_CYCLE
        );
        //When
        NextPaymentCycleSummary summary = childDateOfBirthCalculator.getNextPaymentCycleSummary(paymentCycle);
        //Then
        NextPaymentCycleSummary expectedSummary = NextPaymentCycleSummary.builder().numberOfChildrenTurningOne(1).numberOfChildrenTurningFour(1).build();
        assertThat(summary).isEqualTo(expectedSummary);
    }

    private PaymentCycle buildPaymentCycleWithChildDobs(LocalDate... childDobs) {
        return aValidPaymentCycleBuilder()
                .childrenDob(List.of(childDobs))
                .build();
    }

}
