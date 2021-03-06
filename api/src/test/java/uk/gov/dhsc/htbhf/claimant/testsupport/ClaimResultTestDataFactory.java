package uk.gov.dhsc.htbhf.claimant.testsupport;

import uk.gov.dhsc.htbhf.claimant.entitlement.VoucherEntitlement;
import uk.gov.dhsc.htbhf.claimant.model.ClaimStatus;
import uk.gov.dhsc.htbhf.claimant.service.ClaimResult;

import java.util.Optional;

import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimTestDataFactory.aClaimWithClaimStatus;
import static uk.gov.dhsc.htbhf.claimant.testsupport.VerificationResultTestDataFactory.anAllMatchedVerificationResult;

public class ClaimResultTestDataFactory {

    public static ClaimResult aClaimResult(ClaimStatus claimStatus, Optional<VoucherEntitlement> voucherEntitlement) {
        return ClaimResult.builder()
                .claim(aClaimWithClaimStatus(claimStatus))
                .voucherEntitlement(voucherEntitlement)
                .verificationResult(anAllMatchedVerificationResult())
                .build();
    }
}
