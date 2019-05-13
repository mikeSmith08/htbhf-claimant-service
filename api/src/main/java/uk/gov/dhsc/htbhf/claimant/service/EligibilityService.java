package uk.gov.dhsc.htbhf.claimant.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.dhsc.htbhf.claimant.entity.Claimant;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityResponse;
import uk.gov.dhsc.htbhf.claimant.repository.ClaimRepository;
import uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus;

import static uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityResponse.buildWithStatus;

@Service
@AllArgsConstructor
public class EligibilityService {

    private final EligibilityClient client;
    private final EligibilityStatusCalculator eligibilityStatusCalculator;
    private final ClaimRepository claimRepository;

    /**
     * Determines the eligibility for a given status. If the claimant's nino is not found in the database,
     * the external eligibility service is called.
     * @param claimant the claimant to check the eligibility for
     * @return an eligibility response for the claimant
     */
    public EligibilityResponse determineEligibility(Claimant claimant) {
        if (claimRepository.liveClaimExistsForNino(claimant.getNino())) {
            return buildWithStatus(EligibilityStatus.DUPLICATE);
        }
        EligibilityResponse eligibilityResponse = client.checkEligibility(claimant);
        EligibilityStatus eligibilityStatus = eligibilityStatusCalculator.determineEligibilityStatus(eligibilityResponse);
        return eligibilityResponse.toBuilder()
                .eligibilityStatus(eligibilityStatus)
                .build();
    }
}