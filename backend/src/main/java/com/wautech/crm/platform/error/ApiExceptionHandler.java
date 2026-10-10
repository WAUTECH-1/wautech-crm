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
import com.wautech.crm.organization.service.OrganizationMembershipNotFoundException;
import com.wautech.crm.organization.service.DuplicateOrganizationMembershipException;
import com.wautech.crm.organization.service.LastOrganizationOwnerException;
import com.wautech.crm.organization.service.OrganizationRoleChangeNotAllowedException;
import com.wautech.crm.identity.service.UserNotFoundException;
import com.wautech.crm.identity.service.DuplicateUserEmailException;
import com.wautech.crm.identity.service.DisabledUserException;
import com.wautech.crm.identity.service.InvalidCredentialsException;
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

    @ExceptionHandler({UserNotFoundException.class, OrganizationMembershipNotFoundException.class})
    ProblemDetail handleIdentityNotFound(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({DuplicateUserEmailException.class, DuplicateOrganizationMembershipException.class,
            DisabledUserException.class})
    ProblemDetail handleIdentityConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(com.wautech.crm.organization.entity.IllegalMembershipTransitionException.class)
    ProblemDetail handleIllegalMembershipTransition(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
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

    @ExceptionHandler(OrganizationRoleChangeNotAllowedException.class)
    ProblemDetail handleRoleChangeNotAllowed(OrganizationRoleChangeNotAllowedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Request is not permitted");
    }

    @ExceptionHandler(LastOrganizationOwnerException.class)
    ProblemDetail handleLastOwner(LastOrganizationOwnerException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
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
