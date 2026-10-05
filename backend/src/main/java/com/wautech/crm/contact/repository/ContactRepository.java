package com.wautech.crm.contact.repository;

import com.wautech.crm.contact.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {
    List<Contact> findAllByArchivedFalseOrderByCreatedAtDesc();
    List<Contact> findAllByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(UUID companyId);
    Optional<Contact> findByIdAndArchivedFalse(UUID id);
}
