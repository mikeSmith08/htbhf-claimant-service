package uk.gov.dhsc.htbhf.claimant.service.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.dhsc.htbhf.claimant.entitlement.PregnancyEntitlementCalculator;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycle;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycleStatus;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityAndEntitlementDecision;
import uk.gov.dhsc.htbhf.claimant.repository.PaymentCycleRepository;

import java.time.LocalDate;

import static uk.gov.dhsc.htbhf.claimant.entity.PaymentCycleStatus.NEW;
import static uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus.ELIGIBLE;

@Service
public class PaymentCycleService {

    private final PaymentCycleRepository paymentCycleRepository;
    private final Integer cycleDurationInDays;
    private final PregnancyEntitlementCalculator pregnancyEntitlementCalculator;
    private final Integer pendingExpiryCycleDurationInDays;

    public PaymentCycleService(PaymentCycleRepository paymentCycleRepository,
                               @Value("${payment-cycle.cycle-duration-in-days}") Integer cycleDurationInDays,
                               @Value("${payment-cycle.pending-expiry-cycle-duration-in-days}") Integer pendingExpiryCycleDurationInDays,
                               PregnancyEntitlementCalculator pregnancyEntitlementCalculator) {
        this.paymentCycleRepository = paymentCycleRepository;
        this.cycleDurationInDays = cycleDurationInDays;
        this.pendingExpiryCycleDurationInDays = pendingExpiryCycleDurationInDays;
        this.pregnancyEntitlementCalculator = pregnancyEntitlementCalculator;
    }

    /**
     * Creates a new payment cycle for the given claim and cycle start date, saving it in the database.
     * The cycle end date is set to the start date plus the payment cycle duration time.
     *
     * @param claim          claim to create a payment cycle for
     * @param cycleStartDate the start date of the new payment cycle
     * @return the new payment cycle.
     */
    public PaymentCycle createAndSavePaymentCycle(Claim claim, LocalDate cycleStartDate) {
        PaymentCycle paymentCycle = PaymentCycle.builder()
                .claim(claim)
                .paymentCycleStatus(NEW)
                .cycleStartDate(cycleStartDate)
                .cycleEndDate(cycleStartDate.plusDays(cycleDurationInDays - 1))
                .build();
        paymentCycleRepository.save(paymentCycle);
        return paymentCycle;
    }

    /**
     * Creates a new payment cycle, saving it in the database.
     * The cycle end date is set to the start date plus the payment cycle duration time.
     * The expected due date is set only if the voucher entitlement contains pregnancy vouchers.
     * EligibilityStatus is set to ELIGIBLE, QualifyingBenefitEligibilityStatus to CONFIRMED.
     *
     * @param claim                             claim to create a payment cycle for
     * @param cycleStartDate                    the start date of the new payment cycle
     * @param eligibilityAndEntitlementDecision the eligibility and entitlement decision
     * @return the new payment cycle.
     */
    public PaymentCycle createAndSavePaymentCycleForEligibleClaim(Claim claim,
                                                                  LocalDate cycleStartDate,
                                                                  EligibilityAndEntitlementDecision eligibilityAndEntitlementDecision) {
        PaymentCycle paymentCycle = PaymentCycle.builder()
                .claim(claim)
                .paymentCycleStatus(NEW)
                .cycleStartDate(cycleStartDate)
                .cycleEndDate(cycleStartDate.plusDays(cycleDurationInDays - 1))
                .eligibilityStatus(ELIGIBLE)
                .identityAndEligibilityResponse(eligibilityAndEntitlementDecision.getIdentityAndEligibilityResponse())
                .expectedDeliveryDate(getExpectedDeliveryDateIfRelevant(claim, cycleStartDate))
                .build();

        paymentCycle.applyVoucherEntitlement(eligibilityAndEntitlementDecision.getVoucherEntitlement());
        paymentCycleRepository.save(paymentCycle);
        return paymentCycle;
    }

    private LocalDate getExpectedDeliveryDateIfRelevant(Claim claim, LocalDate cycleStartDate) {
        if (pregnancyEntitlementCalculator.isEntitledToVoucher(claim.getClaimant().getExpectedDeliveryDate(), cycleStartDate)) {
            return claim.getClaimant().getExpectedDeliveryDate();
        }
        return null;
    }

    /**
     * Save the PaymentCycle to the database.
     *
     * @param paymentCycle The {@link PaymentCycle} to save
     */
    public void savePaymentCycle(PaymentCycle paymentCycle) {
        paymentCycleRepository.save(paymentCycle);
    }

    /**
     * Update and saves the payment cycle with values from the decision.
     *
     * @param paymentCycle payment cycle to update
     * @param decision     decision to update the payment cycle with
     */
    public void updatePaymentCycleFromDecision(PaymentCycle paymentCycle, EligibilityAndEntitlementDecision decision) {
        paymentCycle.setEligibilityStatus(decision.getEligibilityStatus());
        paymentCycle.setIdentityAndEligibilityResponse(decision.getIdentityAndEligibilityResponse());
        paymentCycle.applyVoucherEntitlement(decision.getVoucherEntitlement());
        PaymentCycleStatus paymentCycleStatus = PaymentCycleStatus.getStatusForEligibilityDecision(decision.getEligibilityStatus());
        paymentCycle.setPaymentCycleStatus(paymentCycleStatus);
        LocalDate expectedDeliveryDate = getExpectedDeliveryDateIfRelevant(paymentCycle.getClaim(), paymentCycle.getCycleStartDate());
        paymentCycle.setExpectedDeliveryDate(expectedDeliveryDate);
        paymentCycleRepository.save(paymentCycle);
    }

    /**
     * Update and saves the payment cycle with status and card balance from the given calculation.
     *
     * @param paymentCycle       payment cycle to update
     * @param paymentCalculation the calculation of how much of the entitlement should be paid this cycle
     */
    public void updatePaymentCycleFromCalculation(PaymentCycle paymentCycle, PaymentCalculation paymentCalculation) {
        paymentCycle.setPaymentCycleStatus(paymentCalculation.getPaymentCycleStatus());
        paymentCycle.setCardBalanceInPence(paymentCalculation.getAvailableBalanceInPence());
        paymentCycle.setCardBalanceTimestamp(paymentCalculation.getBalanceTimestamp());
        paymentCycleRepository.save(paymentCycle);
    }

    /**
     * Sets the paymentCycle's end date to the start date plus the pending expiry cycle duration time.
     * @param paymentCycle payment cycle to update.
     */
    public void updateEndDateForClaimBecomingPendingExpiry(PaymentCycle paymentCycle) {
        paymentCycle.setCycleEndDate(paymentCycle.getCycleStartDate().plusDays(pendingExpiryCycleDurationInDays - 1));
        paymentCycleRepository.save(paymentCycle);
    }
}
