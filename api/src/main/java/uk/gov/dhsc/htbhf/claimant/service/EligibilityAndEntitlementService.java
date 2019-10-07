package uk.gov.dhsc.htbhf.claimant.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.dhsc.htbhf.claimant.entitlement.PaymentCycleEntitlementCalculator;
import uk.gov.dhsc.htbhf.claimant.entitlement.PaymentCycleVoucherEntitlement;
import uk.gov.dhsc.htbhf.claimant.entity.Claimant;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycle;
import uk.gov.dhsc.htbhf.claimant.exception.MultipleClaimsWithSameNinoException;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityAndEntitlementDecision;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityResponse;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.QualifyingBenefitEligibilityStatus;
import uk.gov.dhsc.htbhf.claimant.repository.ClaimRepository;
import uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
@Slf4j
public class EligibilityAndEntitlementService {

    private final EligibilityClient client;
    private final DuplicateClaimChecker duplicateClaimChecker;
    private final ClaimRepository claimRepository;
    private final PaymentCycleEntitlementCalculator paymentCycleEntitlementCalculator;

    /**
     * Determines the eligibility and entitlement for the given claimant. If the claimant's NINO is not found in the database,
     * the external eligibility service is called.
     * Claimants determined to be eligible by the external eligibility service must still either be pregnant or have children under 4,
     * otherwise they will be ineligible.
     *
     * @param claimant the claimant to check the eligibility for
     * @return the eligibility and entitlement for the claimant
     */
    public EligibilityAndEntitlementDecision evaluateClaimant(Claimant claimant) {
        log.debug("Looking for live claims for the given NINO");
        List<UUID> liveClaimsWithNino = claimRepository.findLiveClaimsWithNino(claimant.getNino());
        if (liveClaimsWithNino.size() > 1) {
            throw new MultipleClaimsWithSameNinoException(liveClaimsWithNino);
        }
        log.debug("Checking eligibility");
        EligibilityResponse eligibilityResponse = client.checkEligibility(claimant);
        log.debug("Calculating entitlement");
        PaymentCycleVoucherEntitlement entitlement = paymentCycleEntitlementCalculator.calculateEntitlement(
                Optional.ofNullable(claimant.getExpectedDeliveryDate()),
                eligibilityResponse.getDateOfBirthOfChildren(),
                LocalDate.now());
        if (!liveClaimsWithNino.isEmpty()) {
            return buildDecision(eligibilityResponse, entitlement, liveClaimsWithNino.get(0));
        }
        EligibilityResponse potentialDuplicateResponse = checkForDuplicateClaimsFromHousehold(eligibilityResponse);
        return buildDecision(potentialDuplicateResponse, entitlement);
    }

    /**
     * Determines the eligibility and entitlement for the given existing claimant. No check is made on the NINO as they already exist in the
     * database. The eligibility status is checked by calling the external service.
     * Claimants determined to be eligible by the external eligibility service must still either be pregnant or have children under 4,
     * otherwise they will be ineligible.
     *
     * @param claimant       the claimant to check the eligibility for
     * @param cycleStartDate the start date of the payment cycle
     * @param previousCycle  the previous payment cycle
     * @return the eligibility and entitlement for the claimant
     */
    public EligibilityAndEntitlementDecision evaluateExistingClaimant(
            Claimant claimant,
            LocalDate cycleStartDate,
            PaymentCycle previousCycle) {
        EligibilityResponse eligibilityResponse = client.checkEligibility(claimant);
        PaymentCycleVoucherEntitlement entitlement = paymentCycleEntitlementCalculator.calculateEntitlement(
                Optional.ofNullable(claimant.getExpectedDeliveryDate()),
                eligibilityResponse.getDateOfBirthOfChildren(),
                cycleStartDate,
                previousCycle.getVoucherEntitlement());
        return buildDecision(eligibilityResponse, entitlement);
    }

    private EligibilityResponse checkForDuplicateClaimsFromHousehold(EligibilityResponse eligibilityResponse) {
        boolean isDuplicate = duplicateClaimChecker.liveClaimExistsForHousehold(eligibilityResponse);
        EligibilityStatus eligibilityStatus = isDuplicate ? EligibilityStatus.DUPLICATE : eligibilityResponse.getEligibilityStatus();
        return eligibilityResponse.toBuilder()
                .eligibilityStatus(eligibilityStatus)
                .build();
    }

    private EligibilityAndEntitlementDecision buildDecision(EligibilityResponse eligibilityResponse, PaymentCycleVoucherEntitlement entitlement) {
        return buildDecision(eligibilityResponse, entitlement, null);
    }

    private EligibilityAndEntitlementDecision buildDecision(EligibilityResponse eligibilityResponse,
                                                            PaymentCycleVoucherEntitlement entitlement,
                                                            UUID existingClaimId) {
        EligibilityStatus eligibilityStatus = determineEligibilityStatus(eligibilityResponse, entitlement);
        return EligibilityAndEntitlementDecision.builder()
                .eligibilityStatus(eligibilityStatus)
                .qualifyingBenefitEligibilityStatus(QualifyingBenefitEligibilityStatus.fromEligibilityStatus(eligibilityResponse.getEligibilityStatus()))
                .voucherEntitlement(entitlement)
                .dateOfBirthOfChildren(eligibilityResponse.getDateOfBirthOfChildren())
                .dwpHouseholdIdentifier(eligibilityResponse.getDwpHouseholdIdentifier())
                .hmrcHouseholdIdentifier(eligibilityResponse.getHmrcHouseholdIdentifier())
                .existingClaimId(existingClaimId)
                .build();
    }

    private EligibilityStatus determineEligibilityStatus(EligibilityResponse response,
                                                         PaymentCycleVoucherEntitlement voucherEntitlement) {
        if (response.getEligibilityStatus() == EligibilityStatus.ELIGIBLE && voucherEntitlement.getTotalVoucherEntitlement() == 0) {
            return EligibilityStatus.INELIGIBLE;
        }
        return response.getEligibilityStatus();
    }

}
