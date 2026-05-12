package exotic.app.planta.resource.commons;

import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.commons.BackupTotalImportService;
import exotic.app.planta.service.commons.ImportedPasswordSanitizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class ImportacionDatosResourcePasswordSanitizationTest {

    private BackupTotalImportService backupTotalImportService;
    private ImportedPasswordSanitizationService importedPasswordSanitizationService;
    private UserRepository userRepository;
    private ImportacionDatosResource resource;

    @BeforeEach
    void setUp() {
        backupTotalImportService = Mockito.mock(BackupTotalImportService.class);
        importedPasswordSanitizationService = Mockito.mock(ImportedPasswordSanitizationService.class);
        userRepository = Mockito.mock(UserRepository.class);
        resource = new ImportacionDatosResource(
                backupTotalImportService,
                importedPasswordSanitizationService,
                userRepository
        );
    }

    @Test
    void resetNonProductionPasswords_allowsMasterLikeUsers() {
        when(importedPasswordSanitizationService.sanitizeNonPrivilegedUserPasswords())
                .thenReturn(new ImportedPasswordSanitizationService.PasswordSanitizationResult(8, 2, 1));

        ResponseEntity<ImportacionDatosResource.PasswordSanitizationResetResponse> response =
                resource.resetNonProductionPasswords(auth("super_master"));

        assertEquals(OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(8, response.getBody().sanitizedUsers());
        assertEquals(2, response.getBody().privilegedUsersSkipped());
        assertEquals(1, response.getBody().invalidUsersSkipped());
        verify(importedPasswordSanitizationService).sanitizeNonPrivilegedUserPasswords();
    }

    @Test
    void resetNonProductionPasswords_blocksNormalUsers() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> resource.resetNonProductionPasswords(auth("operator"))
        );

        assertEquals(FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(importedPasswordSanitizationService);
    }

    @Test
    void resetNonProductionPasswords_blocksUnauthenticatedRequests() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> resource.resetNonProductionPasswords(null)
        );

        assertEquals(UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(importedPasswordSanitizationService);
    }

    @Test
    void resetNonProductionPasswords_returnsForbiddenWhenEnvironmentBlocksOperation() {
        when(importedPasswordSanitizationService.sanitizeNonPrivilegedUserPasswords())
                .thenThrow(new UnsupportedOperationException("Solo local o staging."));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> resource.resetNonProductionPasswords(auth("master"))
        );

        assertEquals(FORBIDDEN, exception.getStatusCode());
        verify(importedPasswordSanitizationService).sanitizeNonPrivilegedUserPasswords();
    }

    private Authentication auth(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of());
    }
}
