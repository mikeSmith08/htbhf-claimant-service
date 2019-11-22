package uk.gov.dhsc.htbhf.claimant.service;

import uk.gov.dhsc.htbhf.claimant.entity.Claimant;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycle;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityAndEntitlementDecision;

import java.time.LocalDate;

public interface EligibilityAndEntitlementService {

    /**
     * Determines the eligibility and entitlement for the given new claimant.
     *
     * @param claimant the new claimant to check the eligibility for
     * @return the eligibility and entitlement for the claimant
     */
    EligibilityAndEntitlementDecision evaluateNewClaimant(Claimant claimant);

    /**
     * Determines the eligibility and entitlement for the given existing claimant.
     *
     * @param claimant       the claimant to check the eligibility for
     * @param cycleStartDate the start date of the payment cycle
     * @param previousCycle  the previous payment cycle
     * @return the eligibility and entitlement for the claimant
     */
    EligibilityAndEntitlementDecision evaluateClaimantForPaymentCycle(Claimant claimant, LocalDate cycleStartDate, PaymentCycle previousCycle);

}
