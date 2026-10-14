package com.wautech.crm.business;

import org.springframework.http.HttpStatus;

public class BusinessFailure extends RuntimeException {
  public final HttpStatus status;

  public BusinessFailure(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }
}
