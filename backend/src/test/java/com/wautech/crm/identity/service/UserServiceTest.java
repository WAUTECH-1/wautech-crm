package com.wautech.crm.identity.service;

import com.wautech.crm.identity.dto.UserProfileRequest;
import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.repository.UserRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository repository;
    @InjectMocks private UserService service;

    @Test
    void createsNormalizedEnabledUser() {
        when(repository.existsByEmail("person@example.com")).thenReturn(false);
        when(repository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.create(request(" Person@Example.com ", " Ada ", " Lovelace "));

        assertEquals("person@example.com", response.email());
        assertEquals("Ada", response.firstName());
        assertEquals("Lovelace", response.lastName());
        assertTrue(response.enabled());
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        when(repository.existsByEmail("person@example.com")).thenReturn(true);
        assertThrows(DuplicateUserEmailException.class,
                () -> service.create(request("Person@Example.com", "A", "B")));
        verify(repository, never()).save(any());
    }

    @Test
    void updatesProfileAndEnableState() {
        UUID id = UUID.randomUUID();
        User user = new User("person@example.com", "Ada", "Lovelace");
        when(repository.findById(id)).thenReturn(Optional.of(user));
        when(repository.existsByEmailAndIdNot("new@example.com", id)).thenReturn(false);
        when(repository.save(user)).thenReturn(user);

        assertEquals("new@example.com", service.updateProfile(id,
                request("NEW@example.com", "Augusta", "King")).email());
        assertFalse(service.setEnabled(id, false).enabled());
        assertThrows(DisabledUserException.class, () -> service.requireEnabledUser(id));
        assertTrue(service.setEnabled(id, true).enabled());
    }

    @Test
    void reportsMissingUser() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> service.getById(id));
    }

    @Test
    void profileRequestRequiresNamesAndValidEmail() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(request("not-email", " ", ""));
            assertEquals(3, violations.size());
        }
    }

    private UserProfileRequest request(String email, String firstName, String lastName) {
        return new UserProfileRequest(email, firstName, lastName);
    }
}
