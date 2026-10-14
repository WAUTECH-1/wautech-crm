package com.wautech.crm.intelligence;

import org.springframework.http.HttpStatus;

public class AiFailure extends RuntimeException {
  public final HttpStatus status;

  public AiFailure(HttpStatus status, String safeMessage) {
    super(safeMessage);
    this.status = status;
  }
}
