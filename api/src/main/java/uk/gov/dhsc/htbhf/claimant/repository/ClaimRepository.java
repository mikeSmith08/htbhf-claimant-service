package uk.gov.dhsc.htbhf.claimant.repository;

import org.javers.spring.annotation.JaversSpringDataAuditable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for the Claim table.
 * For all methods regarding live claims, a claim is live if it's claim status is one of 'NEW', 'ACTIVE', 'PENDING' or 'PENDING_EXPIRY'.
 * All updates to a {@link Claim} and it's child entities are audited using Javers.
 * @see <a href="https://javers.org/">https://javers.org/</a> for more information.
 */
@JaversSpringDataAuditable
public interface ClaimRepository extends CrudRepository<Claim, UUID>, ClaimLazyLoader {

    @Query("SELECT claim.id "
            + "FROM Claim claim "
            + "WHERE claim.claimant.nino = :nino "
            + "AND claim.claimStatus in ('NEW', 'ACTIVE', 'PENDING', 'PENDING_EXPIRY')"
            + "ORDER BY claim.claimStatusTimestamp DESC")
    List<UUID> findLiveClaimsWithNino(@Param("nino") String nino);

    @Query("SELECT COUNT(claim) "
            + "FROM Claim claim "
            + "WHERE claim.dwpHouseholdIdentifier = :dwpHouseholdIdentifier "
            + "AND claim.claimStatus in ('NEW', 'ACTIVE', 'PENDING', 'PENDING_EXPIRY')")
    Long countLiveClaimsWithDwpHouseholdIdentifier(@Param("dwpHouseholdIdentifier") String dwpHouseholdIdentifier);

    @Query("SELECT COUNT(claim) "
            + "FROM Claim claim "
            + "WHERE claim.hmrcHouseholdIdentifier = :hmrcHouseholdIdentifier "
            + "AND claim.claimStatus in ('NEW', 'ACTIVE', 'PENDING', 'PENDING_EXPIRY')")
    Long countLiveClaimsWithHmrcHouseholdIdentifier(@Param("hmrcHouseholdIdentifier") String hmrcHouseholdIdentifier);

    @Query("SELECT claim.id FROM Claim claim where claim.claimStatus = 'NEW'")
    List<UUID> getNewClaimIds();

    default boolean liveClaimExistsForDwpHousehold(String dwpHouseholdIdentifier) {
        return countLiveClaimsWithDwpHouseholdIdentifier(dwpHouseholdIdentifier) != 0;
    }

    default boolean liveClaimExistsForHmrcHousehold(String hmrcHouseholdIdentifier) {
        return countLiveClaimsWithHmrcHouseholdIdentifier(hmrcHouseholdIdentifier) != 0;
    }
}