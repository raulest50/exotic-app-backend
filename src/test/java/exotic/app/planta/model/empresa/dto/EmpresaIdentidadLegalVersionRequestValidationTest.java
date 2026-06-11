package exotic.app.planta.model.empresa.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmpresaIdentidadLegalVersionRequestValidationTest {

    @Test
    void validation_rejectsRequiredFieldsAndInvalidEmail() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        EmpresaIdentidadLegalVersionRequest request = validRequest();
        request.setRazonSocial("");
        request.setEmailPrincipal("correo-invalido");

        Set<String> invalidFields = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(invalidFields.contains("razonSocial"));
        assertTrue(invalidFields.contains("emailPrincipal"));
    }

    private static EmpresaIdentidadLegalVersionRequest validRequest() {
        EmpresaIdentidadLegalVersionRequest request = new EmpresaIdentidadLegalVersionRequest();
        request.setRazonSocial("Napolitana J.P S.A.S.");
        request.setNombreComercial("EXOTIC EXPERT");
        request.setTipoIdentificacion("NIT");
        request.setNumeroIdentificacion("901751897");
        request.setDigitoVerificacion("1");
        request.setTelefonoPrincipal("301 711 51 81");
        request.setEmailPrincipal("produccion.exotic@gmail.com");
        request.setMotivoCambio("Carga inicial");
        return request;
    }
}
