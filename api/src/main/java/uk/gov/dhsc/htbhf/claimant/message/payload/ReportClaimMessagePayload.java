package uk.gov.dhsc.htbhf.claimant.message.payload;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ReportClaimMessagePayload implements MessagePayload {
    private UUID claimId;
}
