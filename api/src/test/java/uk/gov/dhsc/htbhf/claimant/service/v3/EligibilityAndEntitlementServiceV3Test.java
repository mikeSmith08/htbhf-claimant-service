package uk.gov.dhsc.htbhf.claimant.service.v3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dhsc.htbhf.claimant.entitlement.PaymentCycleEntitlementCalculator;
import uk.gov.dhsc.htbhf.claimant.entitlement.PaymentCycleVoucherEntitlement;
import uk.gov.dhsc.htbhf.claimant.entity.Claimant;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycle;
import uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityAndEntitlementDecision;
import uk.gov.dhsc.htbhf.claimant.repository.ClaimRepository;
import uk.gov.dhsc.htbhf.claimant.service.DuplicateClaimChecker;
import uk.gov.dhsc.htbhf.claimant.service.EligibilityAndEntitlementDecisionFactory;
import uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleTestDataFactory;
import uk.gov.dhsc.htbhf.eligibility.model.CombinedIdentityAndEligibilityResponse;
import uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus;
import uk.gov.dhsc.htbhf.eligibility.model.testhelper.CombinedIdentityAndEligibilityResponseTestDataFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimantTestDataFactory.aValidClaimant;
import static uk.gov.dhsc.htbhf.claimant.testsupport.EligibilityAndEntitlementTestDataFactory.aDecisionWithStatus;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleVoucherEntitlementTestDataFactory.aPaymentCycleVoucherEntitlementWithVouchers;
import static uk.gov.dhsc.htbhf.claimant.testsupport.TestConstants.EXPECTED_DELIVERY_DATE_IN_TWO_MONTHS;
import static uk.gov.dhsc.htbhf.dwp.testhelper.TestConstants.HOMER_NINO_V1;
import static uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus.ELIGIBLE;
import static uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus.INELIGIBLE;

@ExtendWith(MockitoExtension.class)
class EligibilityAndEntitlementServiceV3Test {

    private static final boolean NOT_DUPLICATE = false;
    private static final boolean DUPLICATE = true;
    private static final CombinedIdentityAndEligibilityResponse IDENTITY_AND_ELIGIBILITY_RESPONSE =
            CombinedIdentityAndEligibilityResponseTestDataFactory.anIdentityMatchedEligibilityConfirmedUCResponseWithAllMatches();
    private static final List<LocalDate> DATE_OF_BIRTH_OF_CHILDREN = IDENTITY_AND_ELIGIBILITY_RESPONSE.getDobOfChildrenUnder4();
    private static final PaymentCycleVoucherEntitlement VOUCHER_ENTITLEMENT = aPaymentCycleVoucherEntitlementWithVouchers();
    private static final Claimant CLAIMANT = aValidClaimant();
    private static final UUID NO_EXISTING_CLAIM = null;

    @InjectMocks
    EligibilityAndEntitlementServiceV3 eligibilityAndEntitlementServiceV3;

    @Mock
    EligibilityClientV3 client;

    @Mock
    DuplicateClaimChecker duplicateClaimChecker;

    @Mock
    ClaimRepository claimRepository;

    @Mock
    PaymentCycleEntitlementCalculator paymentCycleEntitlementCalculator;

    @Mock
    EligibilityAndEntitlementDecisionFactory eligibilityAndEntitlementDecisionFactory;

    @Test
    void shouldReturnExistingClaimIdWhenLiveClaimAlreadyExists() {
        //Given
        UUID existingClaimId = UUID.randomUUID();
        setupCommonMocks(existingClaimId);
        EligibilityAndEntitlementDecision decisionResponse = setupEligibilityAndEntitlementDecisionFactory(INELIGIBLE);

        //When
        EligibilityAndEntitlementDecision decision = eligibilityAndEntitlementServiceV3.evaluateNewClaimant(CLAIMANT);

        //Then
        assertThat(decision).isEqualTo(decisionResponse);
        verifyCommonMocks(existingClaimId, NOT_DUPLICATE);
        verifyNoInteractions(duplicateClaimChecker);
    }

    @Test
    void shouldReturnEligibleWhenNotDuplicateAndEligible() {
        //Given
        setupCommonMocks(NO_EXISTING_CLAIM);
        given(duplicateClaimChecker.liveClaimExistsForHousehold(any(CombinedIdentityAndEligibilityResponse.class))).willReturn(NOT_DUPLICATE);
        EligibilityAndEntitlementDecision decisionResponse = setupEligibilityAndEntitlementDecisionFactory(ELIGIBLE);

        //When
        EligibilityAndEntitlementDecision result = eligibilityAndEntitlementServiceV3.evaluateNewClaimant(CLAIMANT);

        //Then
        assertThat(result).isEqualTo(decisionResponse);
        verifyCommonMocks(NO_EXISTING_CLAIM, NOT_DUPLICATE);
        verify(duplicateClaimChecker).liveClaimExistsForHousehold(IDENTITY_AND_ELIGIBILITY_RESPONSE);
    }

    @Test
    void shouldReturnDuplicateForExistingHousehold() {
        //Given
        setupCommonMocks(NO_EXISTING_CLAIM);
        given(duplicateClaimChecker.liveClaimExistsForHousehold(any(CombinedIdentityAndEligibilityResponse.class))).willReturn(DUPLICATE);
        EligibilityAndEntitlementDecision decisionResponse = setupEligibilityAndEntitlementDecisionFactory(EligibilityStatus.DUPLICATE);

        //When
        EligibilityAndEntitlementDecision result = eligibilityAndEntitlementServiceV3.evaluateNewClaimant(CLAIMANT);

        //Then
        assertThat(result).isEqualTo(decisionResponse);
        verifyCommonMocks(NO_EXISTING_CLAIM, DUPLICATE);
        verify(duplicateClaimChecker).liveClaimExistsForHousehold(IDENTITY_AND_ELIGIBILITY_RESPONSE);
    }

    @Test
    void shouldReturnEligibleForExistingClaimantWithVouchers() {
        //Given
        PaymentCycle previousCycle = PaymentCycleTestDataFactory.aValidPaymentCycle();
        given(client.checkIdentityAndEligibility(any())).willReturn(IDENTITY_AND_ELIGIBILITY_RESPONSE);
        given(paymentCycleEntitlementCalculator.calculateEntitlement(any(), any(), any(), any())).willReturn(VOUCHER_ENTITLEMENT);
        EligibilityAndEntitlementDecision decisionResponse = setupEligibilityAndEntitlementDecisionFactory(ELIGIBLE);
        LocalDate cycleStartDate = LocalDate.now().minusDays(1);

        //When
        EligibilityAndEntitlementDecision result = eligibilityAndEntitlementServiceV3.evaluateClaimantForPaymentCycle(CLAIMANT, cycleStartDate, previousCycle);

        //Then
        assertThat(result).isEqualTo(decisionResponse);
        verify(client).checkIdentityAndEligibility(CLAIMANT);
        verify(paymentCycleEntitlementCalculator).calculateEntitlement(
                Optional.of(EXPECTED_DELIVERY_DATE_IN_TWO_MONTHS),
                DATE_OF_BIRTH_OF_CHILDREN,
                cycleStartDate,
                previousCycle.getVoucherEntitlement());
        verifyDecisionFactoryCalledCorrectly(NO_EXISTING_CLAIM, NOT_DUPLICATE);
        verifyNoInteractions(duplicateClaimChecker, claimRepository);
    }

    private EligibilityAndEntitlementDecision setupEligibilityAndEntitlementDecisionFactory(EligibilityStatus status) {
        EligibilityAndEntitlementDecision decisionResponse = aDecisionWithStatus(status);
        given(eligibilityAndEntitlementDecisionFactory.buildDecision(any(), any(), any(), anyBoolean())).willReturn(decisionResponse);
        return decisionResponse;
    }

    private void setupCommonMocks(UUID existingClaimId) {
        given(client.checkIdentityAndEligibility(any())).willReturn(IDENTITY_AND_ELIGIBILITY_RESPONSE);
        given(paymentCycleEntitlementCalculator.calculateEntitlement(any(), any(), any())).willReturn(VOUCHER_ENTITLEMENT);
        given(claimRepository.findLiveClaimWithNino(any())).willReturn(Optional.ofNullable(existingClaimId));
    }

    private void verifyDecisionFactoryCalledCorrectly(UUID existingClaimId, boolean duplicate) {
        verify(eligibilityAndEntitlementDecisionFactory).buildDecision(IDENTITY_AND_ELIGIBILITY_RESPONSE,
                VOUCHER_ENTITLEMENT, existingClaimId, duplicate);
    }

    private void verifyCommonMocks(UUID existingClaimId, boolean duplicate) {
        verify(claimRepository).findLiveClaimWithNino(HOMER_NINO_V1);
        verify(client).checkIdentityAndEligibility(CLAIMANT);
        verify(paymentCycleEntitlementCalculator).calculateEntitlement(
                Optional.of(EXPECTED_DELIVERY_DATE_IN_TWO_MONTHS),
                DATE_OF_BIRTH_OF_CHILDREN,
                LocalDate.now());
        verifyDecisionFactoryCalledCorrectly(existingClaimId, duplicate);
    }

}