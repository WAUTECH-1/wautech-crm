package com.wautech.crm.datatransfer;

import com.wautech.crm.audit.service.AuditEventWriter;
import com.wautech.crm.company.dto.CompanyRequest;
import com.wautech.crm.company.dto.CompanyResponse;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyService;
import com.wautech.crm.contact.dto.ContactRequest;
import com.wautech.crm.contact.dto.ContactResponse;
import com.wautech.crm.contact.entity.Contact;
import com.wautech.crm.contact.repository.ContactRepository;
import com.wautech.crm.contact.service.ContactService;
import com.wautech.crm.lead.dto.LeadRequest;
import com.wautech.crm.lead.dto.LeadResponse;
import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.lead.entity.LeadStatus;
import com.wautech.crm.lead.repository.LeadRepository;
import com.wautech.crm.lead.service.LeadService;
import com.wautech.crm.opportunity.dto.OpportunityRequest;
import com.wautech.crm.opportunity.dto.OpportunityResponse;
import com.wautech.crm.opportunity.entity.Opportunity;
import com.wautech.crm.opportunity.entity.OpportunityStage;
import com.wautech.crm.opportunity.repository.OpportunityRepository;
import com.wautech.crm.opportunity.service.OpportunityService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
public class DataTransferService {
    public static final int MAX_EXPORT_ROWS = 1_000;
    private static final int PAGE_SIZE = 250;
    private static final List<String> COMPANY_IMPORT = List.of("name", "website", "industry", "phone", "email", "status");
    private static final List<String> CONTACT_IMPORT = List.of("companyId", "firstName", "lastName", "email", "phone", "jobTitle", "status");
    private static final List<String> LEAD_IMPORT = List.of("firstName", "lastName", "email", "phone", "jobTitle", "companyId");
    private static final List<String> OPPORTUNITY_IMPORT = List.of("name", "description", "amount", "currency", "stage", "expectedCloseDate", "companyId", "contactId");
    private static final Map<ResourceType, List<String>> EXPORT_HEADERS = Map.of(
            ResourceType.COMPANIES, List.of("id", "name", "website", "industry", "phone", "email", "status", "createdAt", "updatedAt"),
            ResourceType.CONTACTS, List.of("id", "companyId", "firstName", "lastName", "email", "phone", "jobTitle", "status", "createdAt", "updatedAt"),
            ResourceType.LEADS, List.of("id", "companyId", "firstName", "lastName", "email", "phone", "jobTitle", "status", "createdAt", "updatedAt"),
            ResourceType.OPPORTUNITIES, List.of("id", "name", "description", "amount", "currency", "stage", "expectedCloseDate", "companyId", "contactId", "createdAt", "updatedAt"));

    private final CsvImportParser parser;
    private final Validator validator;
    private final CompanyService companyService;
    private final ContactService contactService;
    private final LeadService leadService;
    private final OpportunityService opportunityService;
    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final AuditEventWriter auditEventWriter;

    public DataTransferService(CsvImportParser parser, Validator validator, CompanyService companyService,
            ContactService contactService, LeadService leadService, OpportunityService opportunityService,
            CompanyRepository companyRepository, ContactRepository contactRepository, LeadRepository leadRepository,
            OpportunityRepository opportunityRepository, AuditEventWriter auditEventWriter) {
        this.parser = parser;
        this.validator = validator;
        this.companyService = companyService;
        this.contactService = contactService;
        this.leadService = leadService;
        this.opportunityService = opportunityService;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.leadRepository = leadRepository;
        this.opportunityRepository = opportunityRepository;
        this.auditEventWriter = auditEventWriter;
    }

    @PreAuthorize("@crmAuthorization.canImport(#p0)")
    public ImportPreviewResponse preview(UUID organizationId, UUID actorId, ResourceType resource, byte[] file) {
        PreparedImport prepared;
        try {
            prepared = prepare(organizationId, resource, parser.parse(file, importHeaders(resource)));
        } catch (CsvFileException failure) {
            auditEventWriter.recordDataTransferFailure(organizationId, actorId, "IMPORT", resource.path(), 0, failure.getFailureCode());
            throw failure;
        }
        ImportPreviewResponse response = prepared.response();
        auditEventWriter.recordDataTransfer(organizationId, actorId, "DATA_IMPORT_PREVIEWED", resource.path(), response.validRows());
        return response;
    }

    @Transactional
    @PreAuthorize("@crmAuthorization.canImport(#p0)")
    public ImportResultResponse importCsv(UUID organizationId, UUID actorId, ResourceType resource, byte[] file) {
        PreparedImport prepared;
        try {
            prepared = prepare(organizationId, resource, parser.parse(file, importHeaders(resource)));
        } catch (CsvFileException failure) {
            auditEventWriter.recordDataTransferFailure(organizationId, actorId, "IMPORT", resource.path(), 0, failure.getFailureCode());
            throw failure;
        }
        ImportPreviewResponse validation = prepared.response();
        if (validation.invalidRows() > 0) {
            auditEventWriter.recordDataTransferFailure(organizationId, actorId, "IMPORT", resource.path(), validation.totalRows(), "VALIDATION_FAILED");
            throw new CsvImportValidationException(validation);
        }
        try {
            for (PreparedRow row : prepared.rows()) create(organizationId, resource, row.request());
            auditEventWriter.recordDataTransfer(organizationId, actorId, "DATA_IMPORT_SUCCEEDED", resource.path(), prepared.rows().size());
            return new ImportResultResponse(prepared.rows().size(), prepared.rows().size());
        } catch (RuntimeException failure) {
            auditEventWriter.recordDataTransferFailure(organizationId, actorId, "IMPORT", resource.path(), prepared.rows().size(), "IMPORT_FAILED");
            throw failure;
        }
    }

    @Transactional
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public byte[] exportCsv(UUID organizationId, UUID actorId, ResourceType resource) {
        try {
            List<List<String>> records = switch (resource) {
                case COMPANIES -> exportCompanies(organizationId);
                case CONTACTS -> exportContacts(organizationId);
                case LEADS -> exportLeads(organizationId);
                case OPPORTUNITIES -> exportOpportunities(organizationId);
            };
            byte[] csv = writeCsv(resource, records);
            auditEventWriter.recordDataTransfer(organizationId, actorId, "DATA_EXPORT_SUCCEEDED", resource.path(), records.size());
            return csv;
        } catch (RuntimeException failure) {
            auditEventWriter.recordDataTransferFailure(organizationId, actorId, "EXPORT", resource.path(), 0, "EXPORT_FAILED");
            throw failure;
        }
    }

    private PreparedImport prepare(UUID organizationId, ResourceType resource, CsvImportParser.ParsedCsv parsed) {
        List<ImportRowError> errors = new ArrayList<>(parsed.errors());
        List<PreparedRow> valid = new ArrayList<>();
        for (CsvImportParser.CsvRow row : parsed.rows()) {
            Object request = request(resource, row, errors);
            if (request == null || !validateRequest(request, row.rowNumber(), errors)) continue;
            if (!validateReferences(organizationId, resource, request, row.rowNumber(), errors)) continue;
            valid.add(new PreparedRow(row.rowNumber(), request));
        }
        ImportPreviewResponse response = new ImportPreviewResponse(parsed.totalRows(), valid.size(),
                parsed.totalRows() - valid.size(), List.copyOf(errors));
        return new PreparedImport(List.copyOf(valid), response);
    }

    private boolean validateReferences(UUID organizationId, ResourceType resource, Object request, int row,
            List<ImportRowError> errors) {
        try {
            if (request instanceof ContactRequest contact) companyService.getById(organizationId, contact.companyId());
            if (request instanceof LeadRequest lead && lead.companyId() != null) companyService.getById(organizationId, lead.companyId());
            if (request instanceof OpportunityRequest opportunity) {
                CompanyResponse company = companyService.getById(organizationId, opportunity.companyId());
                if (opportunity.contactId() != null) {
                    ContactResponse contact = contactService.getById(organizationId, opportunity.contactId());
                    if (!contact.companyId().equals(company.id())) {
                        addError(errors, new ImportRowError(row, "contactId", "Contact must belong to the selected company"));
                        return false;
                    }
                }
            }
            return true;
        } catch (RuntimeException missingOrUnauthorizedReference) {
            String column = request instanceof ContactRequest ? "companyId"
                    : request instanceof LeadRequest ? "companyId"
                    : request instanceof OpportunityRequest opportunity && opportunity.contactId() != null ? "contactId" : "companyId";
            addError(errors, new ImportRowError(row, column, "Referenced record is unavailable in this organization"));
            return false;
        }
    }

    private Object request(ResourceType resource, CsvImportParser.CsvRow row, List<ImportRowError> errors) {
        Map<String, String> f = row.values();
        try {
            return switch (resource) {
                case COMPANIES -> new CompanyRequest(f.get("name"), emptyToNull(f.get("website")), emptyToNull(f.get("industry")),
                        emptyToNull(f.get("phone")), emptyToNull(f.get("email")), emptyToNull(f.get("status")));
                case CONTACTS -> new ContactRequest(uuid(f.get("companyId")), f.get("firstName"), f.get("lastName"),
                        emptyToNull(f.get("email")), emptyToNull(f.get("phone")), emptyToNull(f.get("jobTitle")), emptyToNull(f.get("status")));
                case LEADS -> new LeadRequest(optionalUuid(f.get("companyId"), "companyId"), f.get("firstName"), f.get("lastName"),
                        emptyToNull(f.get("email")), emptyToNull(f.get("phone")), emptyToNull(f.get("jobTitle")));
                case OPPORTUNITIES -> new OpportunityRequest(f.get("name"), emptyToNull(f.get("description")), decimal(f.get("amount"), "amount"),
                        emptyToNull(f.get("currency")), stage(f.get("stage")), date(f.get("expectedCloseDate"), "expectedCloseDate"),
                        uuid(f.get("companyId"), "companyId"), optionalUuid(f.get("contactId"), "contactId"));
            };
        } catch (IllegalArgumentException | DateTimeParseException invalidValue) {
            String column = invalidValue.getMessage() != null && invalidValue.getMessage().startsWith("CSV_COLUMN:")
                    ? invalidValue.getMessage().substring("CSV_COLUMN:".length()) : "";
            addError(errors, new ImportRowError(row.rowNumber(), column, "Value has an invalid format"));
            return null;
        }
    }

    private boolean validateRequest(Object request, int rowNumber, List<ImportRowError> errors) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        for (ConstraintViolation<Object> violation : violations) {
            addError(errors, new ImportRowError(rowNumber, violation.getPropertyPath().toString(), violation.getMessage()));
        }
        return violations.isEmpty();
    }

    private Object create(UUID organizationId, ResourceType resource, Object request) {
        return switch (resource) {
            case COMPANIES -> companyService.create(organizationId, (CompanyRequest) request);
            case CONTACTS -> contactService.create(organizationId, (ContactRequest) request);
            case LEADS -> leadService.create(organizationId, (LeadRequest) request);
            case OPPORTUNITIES -> opportunityService.create(organizationId, (OpportunityRequest) request);
        };
    }

    private List<List<String>> exportCompanies(UUID org) {
        return exportPages(page -> companyRepository.findAllByOrganization_IdAndArchivedFalse(org, page), c ->
                List.of(s(c.getId()), s(c.getName()), s(c.getWebsite()), s(c.getIndustry()), s(c.getPhone()), s(c.getEmail()), s(c.getStatus()), s(c.getCreatedAt()), s(c.getUpdatedAt())));
    }
    private List<List<String>> exportContacts(UUID org) {
        return exportPages(page -> contactRepository.findAllByOrganization_IdAndArchivedFalse(org, page), c ->
                List.of(s(c.getId()), s(c.getCompany().getId()), s(c.getFirstName()), s(c.getLastName()), s(c.getEmail()), s(c.getPhone()), s(c.getJobTitle()), s(c.getStatus()), s(c.getCreatedAt()), s(c.getUpdatedAt())));
    }
    private List<List<String>> exportLeads(UUID org) {
        return exportPages(page -> leadRepository.findAllByOrganization_IdAndArchivedFalse(org, page), l ->
                List.of(s(l.getId()), s(l.getCompany() == null ? null : l.getCompany().getId()), s(l.getFirstName()), s(l.getLastName()), s(l.getEmail()), s(l.getPhone()), s(l.getJobTitle()), s(l.getStatus()), s(l.getCreatedAt()), s(l.getUpdatedAt())));
    }
    private List<List<String>> exportOpportunities(UUID org) {
        return exportPages(page -> opportunityRepository.findAllByOrganization_IdAndArchivedFalse(org, page), o ->
                List.of(s(o.getId()), s(o.getName()), s(o.getDescription()), s(o.getAmount()), s(o.getCurrency()), s(o.getStage()), s(o.getExpectedCloseDate()), s(o.getCompany().getId()), s(o.getContact() == null ? null : o.getContact().getId()), s(o.getCreatedAt()), s(o.getUpdatedAt())));
    }

    private <T> List<List<String>> exportPages(Function<PageRequest, Page<T>> fetch, Function<T, List<String>> map) {
        List<List<String>> rows = new ArrayList<>();
        int pageNumber = 0;
        while (rows.size() < MAX_EXPORT_ROWS) {
            Page<T> page = fetch.apply(pageRequest(pageNumber++));
            for (T entity : page.getContent()) {
                if (rows.size() == MAX_EXPORT_ROWS) break;
                rows.add(map.apply(entity));
            }
            if (!page.hasNext()) break;
        }
        return rows;
    }

    private static PageRequest pageRequest(int page) { return PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id")); }
    private static String s(Object value) { return value == null ? "" : value.toString(); }

    private byte[] writeCsv(ResourceType resource, List<List<String>> records) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(bytes, StandardCharsets.UTF_8);
             CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            csv.printRecord(EXPORT_HEADERS.get(resource));
            for (List<String> record : records) csv.printRecord(record.stream().map(DataTransferService::safeSpreadsheetCell).toList());
            csv.flush();
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("CSV export could not be generated", failure);
        }
    }

    static String safeSpreadsheetCell(String value) {
        if (value == null || value.isEmpty()) return "";
        int index = 0;
        while (index < value.length() && (Character.isWhitespace(value.charAt(index)) || Character.isISOControl(value.charAt(index)))) index++;
        if (index < value.length() && "=+-@".indexOf(value.charAt(index)) >= 0) return "'" + value;
        return value;
    }

    private static List<String> importHeaders(ResourceType resource) {
        return switch (resource) {
            case COMPANIES -> COMPANY_IMPORT;
            case CONTACTS -> CONTACT_IMPORT;
            case LEADS -> LEAD_IMPORT;
            case OPPORTUNITIES -> OPPORTUNITY_IMPORT;
        };
    }
    private static void addError(List<ImportRowError> errors, ImportRowError error) {
        if (errors.size() < 200) errors.add(error);
    }
    private static String emptyToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static UUID uuid(String value) { return uuid(value, "companyId"); }
    private static UUID uuid(String value, String column) {
        try { return UUID.fromString(value.trim()); }
        catch (IllegalArgumentException | NullPointerException invalid) { throw new IllegalArgumentException("CSV_COLUMN:" + column, invalid); }
    }
    private static UUID optionalUuid(String value) { return optionalUuid(value, "companyId"); }
    private static UUID optionalUuid(String value, String column) { return value == null || value.isBlank() ? null : uuid(value.trim(), column); }
    private static BigDecimal decimal(String value) { return decimal(value, "amount"); }
    private static BigDecimal decimal(String value, String column) {
        try { return value == null || value.isBlank() ? null : new BigDecimal(value.trim()); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("CSV_COLUMN:" + column, invalid); }
    }
    private static LocalDate date(String value) { return date(value, "expectedCloseDate"); }
    private static LocalDate date(String value, String column) {
        try { return value == null || value.isBlank() ? null : LocalDate.parse(value.trim()); }
        catch (DateTimeParseException invalid) { throw new IllegalArgumentException("CSV_COLUMN:" + column, invalid); }
    }
    private static OpportunityStage stage(String value) {
        try { return value == null || value.isBlank() ? null : OpportunityStage.valueOf(value.trim()); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("CSV_COLUMN:stage", invalid); }
    }

    private record PreparedRow(int rowNumber, Object request) { }
    private record PreparedImport(List<PreparedRow> rows, ImportPreviewResponse response) { }
}
