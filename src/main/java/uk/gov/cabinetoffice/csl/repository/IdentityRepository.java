package uk.gov.cabinetoffice.csl.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.cabinetoffice.csl.domain.Identity;
import uk.gov.cabinetoffice.csl.dto.IdentityDto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface IdentityRepository extends JpaRepository<Identity, Long> {

    Identity findFirstByEmailEqualsIgnoreCase(String email);

    Identity findFirstByActiveTrueAndEmailEqualsIgnoreCase(String email);

    Optional<Identity> findFirstByActiveFalseAndEmailEqualsIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select new uk.gov.cabinetoffice.csl.dto.IdentityDto(i.email, i.uid)" +
            " from Identity i")
    List<IdentityDto> findAllNormalised();

    Optional<Identity> findFirstByUid(String uid);

    @Query("select i from Identity i where i.uid in (?1)")
    List<Identity> findIdentitiesByUids(List<String> uids);

    @Query("select new uk.gov.cabinetoffice.csl.dto.IdentityDto(i)" +
            " from Identity i where i.uid in (?1)")
    List<IdentityDto> findIdentitiesByUidsNormalised(Collection<String> uids);

    Long countByAgencyTokenUid(String uid);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Identity SET agencyTokenUid = null WHERE agencyTokenUid IS NOT NULL" +
            " AND agencyTokenUid = :agencyTokenUid")
    void removeAgencyToken(String agencyTokenUid);

    @Query("""
                select new uk.gov.cabinetoffice.csl.dto.IdentityDto(i)
                from Identity i
                where (:#{#uids == null} = true or i.uid in (:uids))
                and (:#{#emails == null} = true or i.email in (:emails))
            """)
    List<IdentityDto> findIdentitiesByUidsAndEmailsNormalised(@Param("uids") List<String> uids, @Param("emails") List<String> emails);
}
