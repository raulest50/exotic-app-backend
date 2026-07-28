package exotic.app.planta.service.empresa;

import exotic.app.planta.model.empresa.dto.EmpresaIdentidadDocumentalVigenteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmpresaIdentidadDocumentalService {

    private final EmpresaIdentidadLegalService identidadLegalService;
    private final EmpresaLogoDocumentalService logoDocumentalService;

    /**
     * Lee la pareja vigente dentro del mismo snapshot de base de datos. Las dos
     * series siguen siendo independientes; la revision compuesta cambia cuando
     * cambia cualquiera de ellas.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EmpresaIdentidadDocumentalVigenteResponse getVigente() {
        return EmpresaIdentidadDocumentalVigenteResponse.from(
                identidadLegalService.getVigente(),
                logoDocumentalService.getVigenteMetadata()
        );
    }
}
