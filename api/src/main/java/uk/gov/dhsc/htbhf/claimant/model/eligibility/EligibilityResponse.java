package uk.gov.dhsc.htbhf.claimant.model.eligibility;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus;

@Data
@Builder
@AllArgsConstructor
public class EligibilityResponse {

    @JsonProperty("eligibilityStatus")
    private EligibilityStatus eligibilityStatus;

    @JsonProperty("dwpHouseholdIdentifier")
    private String dwpHouseholdIdentifier;

    @JsonProperty("hmrcHouseholdIdentifier")
    private String hmrcHouseholdIdentifier;
}
