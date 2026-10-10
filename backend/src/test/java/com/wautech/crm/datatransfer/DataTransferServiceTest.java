package com.wautech.crm.datatransfer;

import com.wautech.crm.audit.service.AuditEventWriter;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyService;
import com.wautech.crm.company.dto.CompanyResponse;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.contact.repository.ContactRepository;
import com.wautech.crm.contact.service.ContactService;
import com.wautech.crm.contact.dto.ContactResponse;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.lead.repository.LeadRepository;
import com.wautech.crm.lead.service.LeadService;
import com.wautech.crm.opportunity.repository.OpportunityRepository;
import com.wautech.crm.opportunity.service.OpportunityService;
import jakarta.validation.Validation;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataTransferServiceTest {
    private final CsvImportParser parser = new CsvImportParser();
    private final CompanyService companies = mock(CompanyService.class);
    private final ContactService contacts = mock(ContactService.class);
    private final LeadService leads = mock(LeadService.class);
    private final OpportunityService opportunities = mock(OpportunityService.class);
    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final ContactRepository contactRepository = mock(ContactRepository.class);
    private final LeadRepository leadRepository = mock(LeadRepository.class);
    private final OpportunityRepository opportunityRepository = mock(OpportunityRepository.class);
    private final AuditEventWriter audit = mock(AuditEventWriter.class);
    private final DataTransferService service = new DataTransferService(parser,
            Validation.buildDefaultValidatorFactory().getValidator(), companies, contacts, leads, opportunities,
            companyRepository, contactRepository, leadRepository, opportunityRepository, audit);

    @Test
    void previewValidatesWithoutCreatingRecordsAndUsesDomainConstraints() {
        UUID org = UUID.randomUUID();
        var preview = service.preview(org, UUID.randomUUID(), ResourceType.COMPANIES, bytes("name,website,industry,phone,email,status\n\"A, Inc\",https://a.test,Software,,contact@a.test,ACTIVE\n"));
        assertEquals(1, preview.totalRows());
        assertEquals(1, preview.validRows());
        assertEquals(0, preview.invalidRows());
        verifyNoInteractions(companies);

        var invalid = service.preview(org, UUID.randomUUID(), ResourceType.COMPANIES, bytes("name,website,industry,phone,email,status\n ,,,,,\n"));
        assertEquals(1, invalid.invalidRows());
        assertTrue(invalid.errors().stream().anyMatch(error -> error.column().equals("name")));
        verifyNoInteractions(companies);
    }

    @Test
    void importsOnlyAfterEveryRowValidAndUsesExistingCompanyService() {
        UUID org = UUID.randomUUID();
        var result = service.importCsv(org, UUID.randomUUID(), ResourceType.COMPANIES,
                bytes("name,website,industry,phone,email,status\nA,,,,,\nB,,,,,\n"));
        assertEquals(2, result.processedRows());
        assertEquals(2, result.createdRecords());
        verify(companies, times(2)).create(eq(org), any());
        verify(audit).recordDataTransfer(eq(org), any(), eq("DATA_IMPORT_SUCCEEDED"), eq("companies"), eq(2));

        reset(companies);
        assertThrows(CsvImportValidationException.class, () -> service.importCsv(org, UUID.randomUUID(), ResourceType.COMPANIES,
                bytes("name,website,industry,phone,email,status\nA,,,,,\n ,,,,,\n")));
        verifyNoInteractions(companies);
    }

    @Test
    void failedDomainCreateIsReportedAndRethrownForTransactionRollback() {
        UUID org = UUID.randomUUID();
        when(companies.create(eq(org), any())).thenThrow(new IllegalStateException("domain rejected"));
        assertThrows(IllegalStateException.class, () -> service.importCsv(org, UUID.randomUUID(), ResourceType.COMPANIES,
                bytes("name,website,industry,phone,email,status\nA,,,,,\n")));
        verify(audit).recordDataTransferFailure(eq(org), any(), eq("IMPORT"), eq("companies"), eq(1), eq("IMPORT_FAILED"));
    }

    @Test
    void importFailureMarksTheOuterTransactionForRollback() {
        UUID org = UUID.randomUUID();
        when(companies.create(eq(org), any())).thenReturn(null).thenThrow(new IllegalStateException("db write failure"));
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(status);
        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        DataTransferService transactionalService = (DataTransferService) proxyFactory.getProxy();

        assertThrows(IllegalStateException.class, () -> transactionalService.importCsv(org, UUID.randomUUID(),
                ResourceType.COMPANIES, bytes("name,website,industry,phone,email,status\nA,,,,,\nB,,,,,\n")));
        verify(transactionManager).rollback(status);
        verify(transactionManager, never()).commit(status);
    }

    @Test
    void importsContactsLeadsAndOpportunitiesThroughTheirDomainServices() {
        UUID org = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        when(companies.getById(org, companyId)).thenReturn(new CompanyResponse(companyId, "Company", null, null, null, null,
                "ACTIVE", Instant.now(), Instant.now(), false));
        when(contacts.getById(org, contactId)).thenReturn(new ContactResponse(contactId, companyId, "Contact", "Person",
                null, null, null, "ACTIVE", Instant.now(), Instant.now(), false));

        var contactImport = service.importCsv(org, UUID.randomUUID(), ResourceType.CONTACTS,
                bytes("companyId,firstName,lastName,email,phone,jobTitle,status\n" + companyId + ",A,B,,,,ACTIVE\n"));
        assertEquals(1, contactImport.createdRecords());
        verify(contacts).create(eq(org), any());

        var leadImport = service.importCsv(org, UUID.randomUUID(), ResourceType.LEADS,
                bytes("firstName,lastName,email,phone,jobTitle,companyId\nA,B,,,,\n"));
        assertEquals(1, leadImport.createdRecords());
        verify(leads).create(eq(org), any());

        var opportunityImport = service.importCsv(org, UUID.randomUUID(), ResourceType.OPPORTUNITIES,
                bytes("name,description,amount,currency,stage,expectedCloseDate,companyId,contactId\nDeal,\"line 1, line 2\",12.50,USD,PROPOSAL,2026-12-31," + companyId + "," + contactId + "\n"));
        assertEquals(1, opportunityImport.createdRecords());
        verify(opportunities).create(eq(org), any());
    }

    @Test
    void rejectsCrossTenantCompanyReferencesAndInvalidOpportunityEnumsOrDatesBeforeWriting() {
        UUID org = UUID.randomUUID();
        UUID foreignCompanyId = UUID.randomUUID();
        when(companies.getById(eq(org), eq(foreignCompanyId))).thenThrow(new CompanyNotFoundException(foreignCompanyId));
        var foreign = service.preview(org, UUID.randomUUID(), ResourceType.CONTACTS,
                bytes("companyId,firstName,lastName,email,phone,jobTitle,status\n" + foreignCompanyId + ",A,B,,,,\n"));
        assertEquals(1, foreign.invalidRows());
        assertEquals("companyId", foreign.errors().getFirst().column());
        verifyNoInteractions(contacts);

        var badFormat = service.preview(org, UUID.randomUUID(), ResourceType.OPPORTUNITIES,
                bytes("name,description,amount,currency,stage,expectedCloseDate,companyId,contactId\n" +
                        "Bad stage,,,,NOT_A_STAGE,2026-12-31," + UUID.randomUUID() + ",\n" +
                        "Bad date,,,,PROPOSAL,not-a-date," + UUID.randomUUID() + ",\n"));
        assertEquals(2, badFormat.invalidRows());
        assertTrue(badFormat.errors().stream().anyMatch(error -> error.column().equals("stage") || error.column().equals("expectedCloseDate")));
        verifyNoInteractions(opportunities);
    }

    @Test
    void exportHasStableHeadersEscapingAndOnlyUsesTenantAndNonArchivedPageQuery() {
        UUID org = UUID.randomUUID();
        Company company = new Company(new Organization("Org"), "=SUM(1,2)", null, "Software, Services", null, null, "ACTIVE");
        when(companyRepository.findAllByOrganization_IdAndArchivedFalse(eq(org), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(company)));
        String csv = new String(service.exportCsv(org, UUID.randomUUID(), ResourceType.COMPANIES), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("id,name,website,industry,phone,email,status,createdAt,updatedAt\r\n"));
        assertTrue(csv.contains("'=SUM(1,2)"));
        assertTrue(csv.contains("\"Software, Services\""));
        verify(companyRepository).findAllByOrganization_IdAndArchivedFalse(eq(org), any(Pageable.class));
    }

    @Test
    void formulaInjectionIsNeutralizedWithoutChangingOrdinaryValues() {
        assertEquals("'=HYPERLINK(\"https://bad.test\")", DataTransferService.safeSpreadsheetCell("=HYPERLINK(\"https://bad.test\")"));
        assertEquals("'  @SUM(A1)", DataTransferService.safeSpreadsheetCell("  @SUM(A1)"));
        assertEquals("Ordinary value", DataTransferService.safeSpreadsheetCell("Ordinary value"));
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
