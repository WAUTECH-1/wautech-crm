package com.wautech.crm.platform.error;

import com.wautech.crm.activity.service.ActivityNotFoundException;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.contact.service.ContactNotFoundException;
import com.wautech.crm.lead.entity.IllegalLeadStatusTransitionException;
import com.wautech.crm.lead.service.LeadNotFoundException;
import com.wautech.crm.note.service.NoteNotFoundException;
import com.wautech.crm.opportunity.entity.IllegalOpportunityStageTransitionException;
import com.wautech.crm.opportunity.service.OpportunityContactCompanyMismatchException;
import com.wautech.crm.opportunity.service.OpportunityNotFoundException;
import com.wautech.crm.platform.search.InvalidListQueryException;
import com.wautech.crm.task.entity.IllegalTaskStatusTransitionException;
import com.wautech.crm.task.service.TaskNotFoundException;
import com.wautech.crm.savedview.service.SavedViewNotFoundException;
import com.wautech.crm.organization.service.OrganizationNotFoundException;
import com.wautech.crm.platform.tenant.OrganizationContextUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(OrganizationNotFoundException.class)
    ProblemDetail handleOrganizationNotFound(OrganizationNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(OrganizationContextUnavailableException.class)
    ProblemDetail handleOrganizationContextUnavailable(OrganizationContextUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }
    @ExceptionHandler(ActivityNotFoundException.class)
    ProblemDetail handleActivityNotFound(ActivityNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(CompanyNotFoundException.class)
    ProblemDetail handleCompanyNotFound(CompanyNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ContactNotFoundException.class)
    ProblemDetail handleContactNotFound(ContactNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(LeadNotFoundException.class)
    ProblemDetail handleLeadNotFound(LeadNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(NoteNotFoundException.class)
    ProblemDetail handleNoteNotFound(NoteNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(OpportunityNotFoundException.class)
    ProblemDetail handleOpportunityNotFound(OpportunityNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(TaskNotFoundException.class)
    ProblemDetail handleTaskNotFound(TaskNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(SavedViewNotFoundException.class)
    ProblemDetail handleSavedViewNotFound(SavedViewNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({OpportunityContactCompanyMismatchException.class, IllegalOpportunityStageTransitionException.class,
            IllegalTaskStatusTransitionException.class})
    ProblemDetail handleInvalidOperation(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(IllegalLeadStatusTransitionException.class)
    ProblemDetail handleIllegalLeadStatusTransition(IllegalLeadStatusTransitionException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            InvalidListQueryException.class})
    ProblemDetail handleInvalidRequest(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request contains invalid input");
    }
}
