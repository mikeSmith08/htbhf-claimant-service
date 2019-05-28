package uk.gov.dhsc.htbhf.claimant.service.audit;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.entity.Payment;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycle;
import uk.gov.dhsc.htbhf.claimant.model.card.CardResponse;
import uk.gov.dhsc.htbhf.claimant.model.card.DepositFundsResponse;
import uk.gov.dhsc.htbhf.logging.EventLogger;

import java.util.UUID;

/**
 * Component responsible for auditing events around a Claim.
 */
@Component
@AllArgsConstructor
@Slf4j
public class EventAuditor {

    private final EventLogger eventLogger;

    /**
     * Audit a new claim event.
     *
     * @param claim The claim to audit
     */
    public void auditNewClaim(Claim claim) {
        if (claim == null) {
            log.warn("Unable to audit null Claim");
            return;
        }
        NewClaimEvent newClaimEvent = NewClaimEvent.builder()
                .claimId(claim.getId())
                .claimStatus(claim.getClaimStatus())
                .eligibilityStatus(claim.getEligibilityStatus())
                .build();
        eventLogger.logEvent(newClaimEvent);
    }

    public void auditNewCard(UUID claimId, CardResponse cardResponse) {
        if (claimId == null || cardResponse == null) {
            log.warn("Unable to audit new card event with claimId: {} and cardResponse: {}. Both fields must not be null", claimId, cardResponse);
            return;
        }
        NewCardEvent newCardEvent = NewCardEvent.builder()
                .claimId(claimId)
                .cardAccountId(cardResponse.getCardAccountId())
                .build();
        eventLogger.logEvent(newCardEvent);
    }

    public void auditMakePayment(PaymentCycle paymentCycle, Payment payment, DepositFundsResponse depositFundsResponse) {
        MakePaymentEvent event = MakePaymentEvent.builder()
                .claimId(paymentCycle.getClaim().getId())
                .entitlementAmountInPence(paymentCycle.getTotalEntitlementAmountInPence())
                .paymentAmountInPence(payment.getPaymentAmountInPence())
                .paymentId(payment.getId())
                .reference(depositFundsResponse.getReferenceId())
                .build();
        eventLogger.logEvent(event);
    }

    public void auditBalanceTooHighForPayment(UUID claimId, int entitlementAmountInPence, int balanceOnCard) {
        BalanceTooHighForPaymentEvent event = BalanceTooHighForPaymentEvent.builder()
                .claimId(claimId)
                .entitlementAmountInPence(entitlementAmountInPence)
                .balanceOnCard(balanceOnCard)
                .build();
        eventLogger.logEvent(event);
    }
}
