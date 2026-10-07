package com.wautech.crm.contact.repository;

import com.wautech.crm.contact.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {
    List<Contact> findAllByArchivedFalseOrderByCreatedAtDesc();
    List<Contact> findAllByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(UUID companyId);
    @Query("select c from Contact c where c.archived = false and (:companyId is null or c.company.id = :companyId) " +
            "and (:search is null or lower(c.firstName) like :search escape '!' or lower(c.lastName) like :search escape '!' " +
            "or lower(c.email) like :search escape '!' or lower(c.phone) like :search escape '!' " +
            "or lower(c.jobTitle) like :search escape '!') order by c.createdAt desc")
    List<Contact> findActive(@Param("companyId") UUID companyId, @Param("search") String search);
    Optional<Contact> findByIdAndArchivedFalse(UUID id);
}
