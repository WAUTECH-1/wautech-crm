package com.wautech.crm.identity.service;

import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CredentialProvisioningServiceTest {
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private UserRepository repository;
    @Mock private UserService userService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private CredentialProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new CredentialProvisioningService(repository, userService, encoder);
        lenient().when(userService.requireUser(USER_ID)).thenReturn(new User("user@example.test", "First", "Last"));
    }

    @Test
    void provisionsOnlyAHashAndCanBeVerifiedWithTheRawPassword() {
        when(repository.provisionPasswordHashIfMissing(eq(USER_ID), anyString())).thenReturn(1);

        service.provisionInitialPassword(USER_ID, "Long-enough-password-1");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).provisionPasswordHashIfMissing(eq(USER_ID), hash.capture());
        assertNotEquals("Long-enough-password-1", hash.getValue());
        assertTrue(encoder.matches("Long-enough-password-1", hash.getValue()));
    }

    @Test
    void rejectsPasswordsOutsideBcryptByteLimitsWithoutWriting() {
        assertThrows(InvalidPasswordException.class, () -> service.provisionInitialPassword(USER_ID, "short"));
        assertThrows(InvalidPasswordException.class,
                () -> service.provisionInitialPassword(USER_ID, "é".repeat(37)));
        assertThrows(InvalidPasswordException.class, () -> service.provisionInitialPassword(USER_ID, null));
        verifyNoInteractions(repository);
    }

    @Test
    void concurrentOrRepeatedProvisioningCannotReplaceTheFirstCredential() {
        when(repository.provisionPasswordHashIfMissing(eq(USER_ID), anyString())).thenReturn(0);

        assertThrows(CredentialAlreadyProvisionedException.class,
                () -> service.provisionInitialPassword(USER_ID, "Long-enough-password-1"));
    }
}
