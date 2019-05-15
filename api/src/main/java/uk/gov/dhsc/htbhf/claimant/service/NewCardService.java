package uk.gov.dhsc.htbhf.claimant.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.factory.CardRequestFactory;
import uk.gov.dhsc.htbhf.claimant.model.card.CardRequest;
import uk.gov.dhsc.htbhf.claimant.model.card.CardResponse;
import uk.gov.dhsc.htbhf.claimant.repository.ClaimRepository;
import uk.gov.dhsc.htbhf.claimant.service.audit.ClaimAuditor;
import uk.gov.dhsc.htbhf.claimant.service.payments.PaymentCycleService;

import java.util.UUID;
import javax.persistence.EntityNotFoundException;

@Service
@AllArgsConstructor
@Slf4j
public class NewCardService {

    private CardClient cardClient;
    private CardRequestFactory cardRequestFactory;
    private ClaimRepository claimRepository;
    private ClaimAuditor claimAuditor;
    private PaymentCycleService paymentCycleService;

    @Transactional
    public void createNewCard(UUID claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new EntityNotFoundException("Unable to find claim with id " + claimId));
        CardRequest cardRequest = cardRequestFactory.createCardRequest(claim);
        CardResponse cardResponse = cardClient.requestNewCard(cardRequest);
        saveClaimWithCardId(claim, cardResponse);
        claimAuditor.auditNewCard(claimId, cardResponse);
        paymentCycleService.createAndSavePaymentCycle(claim, claim.getClaimStatusTimestamp().toLocalDate());
    }

    private void saveClaimWithCardId(Claim claim, CardResponse cardResponse) {
        claim.setCardAccountId(cardResponse.getCardAccountId());
        claimRepository.save(claim);
    }
}