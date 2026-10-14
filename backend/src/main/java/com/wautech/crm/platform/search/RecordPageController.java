package com.wautech.crm.platform.search;

import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/records")
public class RecordPageController {
  private final RecordPageService service;
  private final AuthenticatedOrganizationContext context;

  public RecordPageController(RecordPageService s, AuthenticatedOrganizationContext c) {
    service = s;
    context = c;
  }

  @GetMapping("/{resource}")
  public RecordPageService.RecordPage list(
      @PathVariable String resource,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestParam Map<String, String> filters) {
    return service.list(context.requireOrganizationId(), resource, filters, page, size);
  }
}
