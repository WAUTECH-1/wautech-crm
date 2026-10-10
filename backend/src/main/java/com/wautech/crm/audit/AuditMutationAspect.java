package com.wautech.crm.audit;

import com.wautech.crm.audit.service.AuditEventWriter;
import com.wautech.crm.platform.security.CrmAuthorization;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.util.UUID;

/** Audits successful mutations inside the same transaction as the business method. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuditMutationAspect {
    private final TransactionTemplate transactionTemplate;
    private final AuditEventWriter writer;
    private final CrmAuthorization authorization;

    public AuditMutationAspect(PlatformTransactionManager transactionManager, AuditEventWriter writer,
            CrmAuthorization authorization) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.writer = writer;
        this.authorization = authorization;
    }

    @Around("@annotation(com.wautech.crm.audit.AuditedMutation)")
    public Object audit(ProceedingJoinPoint invocation) throws Throwable {
        Method method = ((MethodSignature) invocation.getSignature()).getMethod();
        AuditedMutation annotation = method.getAnnotation(AuditedMutation.class);
        Object[] args = invocation.getArgs();
        UUID targetId = "create".equals(method.getName()) ? null : findTargetId(null, args);
        UUID organizationId = annotation.organizationScoped()
                ? (args.length > 0 && args[0] instanceof UUID id ? id : null) : null;
        if (targetId == null && "ORGANIZATION".equals(annotation.targetType())
                && args.length > 0 && args[0] instanceof UUID id) targetId = id;
        UUID requestedTargetId = targetId;
        try {
            return transactionTemplate.execute(status -> {
                try {
                    Object result = invocation.proceed();
                    UUID resultId = findTargetId(result, args);
                    UUID eventOrganizationId = organizationId == null && annotation.organizationScoped()
                            ? resultId : organizationId;
                    writer.recordBusiness(eventOrganizationId, authorization.currentUserId(), annotation.eventType(),
                            annotation.targetType(), requestedTargetId == null ? resultId : requestedTargetId);
                    return result;
                } catch (Throwable failure) {
                    status.setRollbackOnly();
                    if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
                    if (failure instanceof Error error) throw error;
                    throw new MutationInvocationException(failure);
                }
            });
        } catch (MutationInvocationException wrapper) {
            throw wrapper.getCause();
        }
    }

    private UUID findTargetId(Object result, Object[] args) {
        if (result != null) {
            try {
                Method getId = result.getClass().getMethod("getId");
                Object id = getId.invoke(result);
                if (id instanceof UUID uuid) return uuid;
            } catch (ReflectiveOperationException ignored) {
                try {
                    Method id = result.getClass().getMethod("id");
                    Object value = id.invoke(result);
                    if (value instanceof UUID uuid) return uuid;
                } catch (ReflectiveOperationException ignoredAgain) { }
            }
        }
        for (int i = 1; i < args.length; i++) if (args[i] instanceof UUID id) return id;
        return null;
    }

    public static class MutationInvocationException extends RuntimeException {
        MutationInvocationException(Throwable cause) { super(cause); }
    }
}
