package com.wautech.crm.audit;

import com.wautech.crm.audit.service.AuditEventWriter;
import com.wautech.crm.platform.security.CrmAuthorization;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditMutationAspectTest {
    private final AuditEventWriter writer = mock(AuditEventWriter.class);
    private final CrmAuthorization authorization = mock(CrmAuthorization.class);
    private final RecordingTransactionManager transactions = new RecordingTransactionManager();
    private final AuditMutationAspect aspect = new AuditMutationAspect(transactions, writer, authorization);

    @Test
    void successfulMutationAndAuditCommitTogether() throws Throwable {
        UUID organizationId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(authorization.currentUserId()).thenReturn(UUID.randomUUID());
        ProceedingJoinPoint invocation = invocation("create", new Object[]{organizationId}, new Result(targetId), null);

        assertEquals(new Result(targetId), aspect.audit(invocation));

        verify(writer).recordBusiness(eq(organizationId), any(), eq("COMPANY_CREATED"), eq("COMPANY"), eq(targetId));
        assertEquals(1, transactions.commits.get());
        assertEquals(0, transactions.rollbacks.get());
    }

    @Test
    void auditFailureRollsBackMutationAndDoesNotReturnSuccess() throws Throwable {
        UUID organizationId = UUID.randomUUID();
        doThrow(new IllegalStateException("audit unavailable"))
                .when(writer).recordBusiness(any(), any(), anyString(), anyString(), any());
        ProceedingJoinPoint invocation = invocation("create", new Object[]{organizationId}, new Result(UUID.randomUUID()), null);

        assertThrows(IllegalStateException.class, () -> aspect.audit(invocation));
        assertEquals(0, transactions.commits.get());
        assertEquals(1, transactions.rollbacks.get());
    }

    @Test
    void businessFailureDoesNotWriteSuccessEvent() throws Throwable {
        IllegalArgumentException failure = new IllegalArgumentException("invalid operation");
        ProceedingJoinPoint invocation = invocation("create", new Object[]{UUID.randomUUID()}, null, failure);
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> aspect.audit(invocation)));
        verifyNoInteractions(writer);
        assertEquals(1, transactions.rollbacks.get());
    }

    private ProceedingJoinPoint invocation(String methodName, Object[] args, Object result, Throwable failure)
            throws Throwable {
        Method method = Fixture.class.getDeclaredMethod(methodName, UUID.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(args);
        if (failure == null) when(point.proceed()).thenReturn(result);
        else when(point.proceed()).thenThrow(failure);
        return point;
    }

    public static class Fixture {
        @AuditedMutation(eventType = "COMPANY_CREATED", targetType = "COMPANY")
        Result create(UUID organizationId) { return new Result(UUID.randomUUID()); }
    }
    public record Result(UUID id) { }

    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        final AtomicInteger commits = new AtomicInteger();
        final AtomicInteger rollbacks = new AtomicInteger();
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected boolean isExistingTransaction(Object transaction) { return false; }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits.incrementAndGet(); }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks.incrementAndGet(); }
    }
}
