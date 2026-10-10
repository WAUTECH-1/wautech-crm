package com.wautech.crm.datatransfer;

import com.wautech.crm.platform.security.CrmAuthorization;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Locale;

@RestController
@RequestMapping("/api")
public class DataTransferController {
    private final DataTransferService service;
    private final CrmAuthorization authorization;

    public DataTransferController(DataTransferService service, CrmAuthorization authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping(path = "/imports/{resourceType}/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@crmAuthorization.canImport(#p0)")
    public ImportPreviewResponse validate(@RequestAttribute("com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext.organizationId") UUID organizationId,
            @PathVariable String resourceType, @RequestParam("file") MultipartFile file) {
        return service.preview(organizationId, authorization.currentUserId(),
                ResourceType.fromPath(resourceType), bytes(file));
    }

    @PostMapping(path = "/imports/{resourceType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@crmAuthorization.canImport(#p0)")
    public ImportResultResponse importCsv(@RequestAttribute("com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext.organizationId") UUID organizationId,
            @PathVariable String resourceType, @RequestParam("file") MultipartFile file) {
        return service.importCsv(organizationId, authorization.currentUserId(),
                ResourceType.fromPath(resourceType), bytes(file));
    }

    @GetMapping(path = "/exports/{resourceType}", produces = "text/csv")
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public ResponseEntity<byte[]> export(@RequestAttribute("com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext.organizationId") UUID organizationId,
            @PathVariable String resourceType) {
        ResourceType resource = ResourceType.fromPath(resourceType);
        byte[] contents = service.exportCsv(organizationId, authorization.currentUserId(), resource);
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(resource.path() + "-v1.csv", StandardCharsets.UTF_8).build().toString())
                .body(contents);
    }

    private static byte[] bytes(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new CsvFileException("Only CSV files are supported", "UNSUPPORTED_FORMAT");
        }
        if (file.getSize() > CsvImportParser.MAX_FILE_BYTES) {
            throw new CsvFileException("CSV file exceeds the 5 MiB limit", "FILE_TOO_LARGE");
        }
        try { return file.getBytes(); }
        catch (IOException failure) { throw new CsvFileException("CSV file could not be read", "FILE_READ_FAILED", failure); }
    }
}
