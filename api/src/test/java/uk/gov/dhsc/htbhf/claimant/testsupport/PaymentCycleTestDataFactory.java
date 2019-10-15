package uk.gov.dhsc.htbhf.claimant.testsupport;

import uk.gov.dhsc.htbhf.claimant.entitlement.PaymentCycleVoucherEntitlement;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.entity.Payment;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycle;
import uk.gov.dhsc.htbhf.claimant.entity.PaymentCycleStatus;
import uk.gov.dhsc.htbhf.eligibility.model.EligibilityStatus;

import java.time.LocalDate;
import java.util.List;

import static java.util.Collections.emptyList;
import static uk.gov.dhsc.htbhf.claimant.entity.PaymentCycleStatus.NEW;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimTestDataFactory.aValidClaim;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleVoucherEntitlementTestDataFactory.aPaymentCycleVoucherEntitlementWithPregnancyVouchers;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleVoucherEntitlementTestDataFactory.aPaymentCycleVoucherEntitlementWithVouchers;
import static uk.gov.dhsc.htbhf.claimant.testsupport.PaymentCycleVoucherEntitlementTestDataFactory.aPaymentCycleVoucherEntitlementWithVouchersFromDate;

public class PaymentCycleTestDataFactory {

    private static final int TOTAL_VOUCHERS = 16;
    public static final int TOTAL_ENTITLEMENT_AMOUNT_IN_PENCE = 4960;

    public static PaymentCycle aValidPaymentCycle() {
        return aValidPaymentCycleBuilder().build();
    }

    public static PaymentCycle aPaymentCycleWithCycleEntitlementAndClaim(PaymentCycleVoucherEntitlement paymentCycleVoucherEntitlement,
                                                                         Claim claim) {
        return aValidPaymentCycleBuilder()
                .voucherEntitlement(paymentCycleVoucherEntitlement)
                .totalVouchers(paymentCycleVoucherEntitlement.getTotalVoucherEntitlement())
                .claim(claim)
                .childrenDob(nullSafeGetChildrenDob(claim))
                .build();
    }

    public static PaymentCycle aPaymentCycleWithStartDateAndClaim(LocalDate startDate,
                                                                  Claim claim) {
        PaymentCycleVoucherEntitlement voucherEntitlement = aPaymentCycleVoucherEntitlementWithVouchersFromDate(startDate);
        return aValidPaymentCycleBuilder()
                .voucherEntitlement(voucherEntitlement)
                .totalVouchers(voucherEntitlement.getTotalVoucherEntitlement())
                .cycleStartDate(startDate)
                .claim(claim)
                .childrenDob(nullSafeGetChildrenDob(claim))
                .build();
    }

    public static PaymentCycle aPaymentCycleWithStartDateClaimAndExpectedDeliveryDate(LocalDate startDate,
                                                                                      Claim claim,
                                                                                      LocalDate expectedDeliveryDate) {
        PaymentCycleVoucherEntitlement voucherEntitlement = aPaymentCycleVoucherEntitlementWithVouchersFromDate(startDate);
        return aValidPaymentCycleBuilder()
                .voucherEntitlement(voucherEntitlement)
                .totalVouchers(voucherEntitlement.getTotalVoucherEntitlement())
                .cycleStartDate(startDate)
                .claim(claim)
                .childrenDob(nullSafeGetChildrenDob(claim))
                .expectedDeliveryDate(expectedDeliveryDate)
                .build();
    }

    public static PaymentCycle aPaymentCycleWithStartAndEndDate(LocalDate startDate, LocalDate endDate) {
        PaymentCycleVoucherEntitlement voucherEntitlement = aPaymentCycleVoucherEntitlementWithVouchersFromDate(startDate);
        return aValidPaymentCycleBuilder()
                .cycleStartDate(startDate)
                .voucherEntitlement(voucherEntitlement)
                .totalVouchers(voucherEntitlement.getTotalVoucherEntitlement())
                .cycleEndDate(endDate)
                .build();
    }

    public static PaymentCycle aPaymentCycleWithPregnancyVouchersOnly(LocalDate startDate, LocalDate endDate) {
        PaymentCycleVoucherEntitlement voucherEntitlement = aPaymentCycleVoucherEntitlementWithPregnancyVouchers();
        return aValidPaymentCycleBuilder()
                .voucherEntitlement(voucherEntitlement)
                .totalVouchers(voucherEntitlement.getTotalVoucherEntitlement())
                .cycleStartDate(startDate)
                .cycleEndDate(endDate)
                .totalEntitlementAmountInPence(voucherEntitlement.getTotalVoucherValueInPence())
                .totalVouchers(4)
                .childrenDob(emptyList())
                .build();
    }

    public static PaymentCycle aPaymentCycleWithPaymentAndClaim(Payment payment, Claim claim) {
        PaymentCycle paymentCycle = aValidPaymentCycleBuilder()
                .claim(claim)
                .childrenDob(nullSafeGetChildrenDob(claim))
                .build();
        paymentCycle.addPayment(payment);
        return paymentCycle;
    }

    public static PaymentCycle aPaymentCycleWithClaim(Claim claim) {
        return aValidPaymentCycleBuilder()
                .claim(claim)
                .childrenDob(nullSafeGetChildrenDob(claim))
                .build();
    }

    public static PaymentCycle aPaymentCycleWithStatus(PaymentCycleStatus status) {
        return aValidPaymentCycleBuilder().paymentCycleStatus(status).build();
    }

    public static PaymentCycle aPaymentCycleWithChildrenDobs(List<LocalDate> childrenDobs) {
        return aValidPaymentCycleBuilder().childrenDob(childrenDobs).build();
    }

    public static PaymentCycle.PaymentCycleBuilder aValidPaymentCycleBuilder() {
        PaymentCycleVoucherEntitlement voucherEntitlement = aPaymentCycleVoucherEntitlementWithVouchers();
        return PaymentCycle.builder()
                .claim(aValidClaim())
                .paymentCycleStatus(NEW)
                .eligibilityStatus(EligibilityStatus.ELIGIBLE)
                .voucherEntitlement(voucherEntitlement)
                .totalVouchers(voucherEntitlement.getTotalVoucherEntitlement())
                .cycleStartDate(LocalDate.now())
                //Next cycle starts 4 weeks after the current one so last day of current cycle is one day less
                .cycleEndDate(LocalDate.now().plusDays(27))
                .totalVouchers(TOTAL_VOUCHERS)
                .childrenDob(List.of(
                        LocalDate.now().minusMonths(6),
                        LocalDate.now().minusYears(3).minusMonths(6)))
                .totalEntitlementAmountInPence(TOTAL_ENTITLEMENT_AMOUNT_IN_PENCE);
    }

    private static List<LocalDate> nullSafeGetChildrenDob(Claim claim) {
        return (claim == null) ? emptyList() : claim.getClaimant().getChildrenDob();
    }
}
