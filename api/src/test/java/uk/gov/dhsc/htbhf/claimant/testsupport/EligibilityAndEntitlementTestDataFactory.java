package uk.gov.dhsc.htbhf.claimant.testsupport;

import uk.gov.dhsc.htbhf.claimant.model.eligibility.EligibilityAndEntitlementDecision;
import uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus;

import java.time.LocalDate;
import java.util.List;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static java.util.Collections.nCopies;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleVoucherEntitlementTestDataFactory.aPaymentCycleVoucherEntitlementWithVouchers;
import static uk.gov.dhsc.htbhf.claimant.testsupport.TestConstants.*;
import static uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus.ELIGIBLE;
import static uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus.INELIGIBLE;

public class EligibilityAndEntitlementTestDataFactory {

    public static EligibilityAndEntitlementDecision aDecisionWithStatus(EligibilityStatus eligibilityStatus) {
        EligibilityAndEntitlementDecision.EligibilityAndEntitlementDecisionBuilder builder = aValidDecisionBuilder().eligibilityStatus(eligibilityStatus);
        if (eligibilityStatus != ELIGIBLE) {
            builder.voucherEntitlement(null);
        }
        return builder.build();
    }

    public static EligibilityAndEntitlementDecision anIneligibleDecisionWithNoChildren() {
        return aValidDecisionBuilder().eligibilityStatus(INELIGIBLE).dateOfBirthOfChildren(emptyList()).build();
    }

    public static EligibilityAndEntitlementDecision.EligibilityAndEntitlementDecisionBuilder aValidDecisionBuilder() {
        List<LocalDate> children = createChildren(NUMBER_OF_CHILDREN_UNDER_ONE, NUMBER_OF_CHILDREN_UNDER_FOUR);
        return EligibilityAndEntitlementDecision.builder()
                .eligibilityStatus(ELIGIBLE)
                .dwpHouseholdIdentifier(DWP_HOUSEHOLD_IDENTIFIER)
                .hmrcHouseholdIdentifier(HMRC_HOUSEHOLD_IDENTIFIER)
                .voucherEntitlement(aPaymentCycleVoucherEntitlementWithVouchers())
                .dateOfBirthOfChildren(children);
    }

    private static List<LocalDate> createChildren(Integer numberOfChildrenUnderOne, Integer numberOfChildrenUnderFour) {
        List<LocalDate> childrenUnderOne = nCopies(numberOfChildrenUnderOne, MAGGIE_DOB);
        List<LocalDate> childrenBetweenOneAndFour = nCopies(numberOfChildrenUnderFour - numberOfChildrenUnderOne, LISA_DOB);
        return newArrayList(concat(childrenUnderOne, childrenBetweenOneAndFour));
    }

}
