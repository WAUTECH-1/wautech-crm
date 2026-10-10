package com.wautech.crm.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class SecurityProblemWriter {
    private SecurityProblemWriter() { }

    public static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
