package exotic.app.planta.service.empresa;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.empresa.dto.EmpresaIdentidadLegalVersionRequest;
import exotic.app.planta.repo.empresa.EmpresaIdentidadLegalVersionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmpresaIdentidadLegalService {

    private final EmpresaIdentidadLegalVersionRepo repo;

    @Transactional(readOnly = true)
    public EmpresaIdentidadLegalVersion getVigente() {
        return repo.findFirstByEstadoOrderByVersionDesc(EmpresaIdentidadLegalVersion.Estado.VIGENTE)
                .orElseThrow(() -> new IllegalStateException("No existe una identidad legal vigente configurada."));
    }

    @Transactional(readOnly = true)
    public List<EmpresaIdentidadLegalVersion> getVersiones() {
        return repo.findAllByOrderByVersionDesc();
    }

    @Transactional(readOnly = true)
    public EmpresaIdentidadLegalVersion getVersion(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la version de identidad legal con id: " + id));
    }

    @Transactional(readOnly = true)
    public EmpresaIdentidadLegalVersion resolveVersionForOcm(Long versionId) {
        if (versionId == null) {
            return getVigente();
        }
        return getVersion(versionId);
    }

    @Transactional
    public EmpresaIdentidadLegalVersion crearNuevaVersion(
            EmpresaIdentidadLegalVersionRequest request,
            String username
    ) {
        LocalDateTime now = AppTime.now();

        repo.findByEstadoForUpdate(EmpresaIdentidadLegalVersion.Estado.VIGENTE)
                .ifPresent(vigente -> {
                    vigente.setEstado(EmpresaIdentidadLegalVersion.Estado.RETIRADA);
                    vigente.setVigenteHasta(now);
                    repo.save(vigente);
                });

        EmpresaIdentidadLegalVersion nueva = new EmpresaIdentidadLegalVersion();
        nueva.setVersion(repo.findMaxVersion() + 1);
        nueva.setEstado(EmpresaIdentidadLegalVersion.Estado.VIGENTE);
        nueva.setRazonSocial(trim(request.getRazonSocial()));
        nueva.setNombreComercial(trim(request.getNombreComercial()));
        nueva.setTipoIdentificacion(trim(request.getTipoIdentificacion()));
        nueva.setNumeroIdentificacion(trim(request.getNumeroIdentificacion()));
        nueva.setDigitoVerificacion(trim(request.getDigitoVerificacion()));
        nueva.setTelefonoPrincipal(trim(request.getTelefonoPrincipal()));
        nueva.setEmailPrincipal(trim(request.getEmailPrincipal()));
        nueva.setVigenteDesde(now);
        nueva.setCreadoEn(now);
        nueva.setCreadoPor(trim(username));
        nueva.setMotivoCambio(trim(request.getMotivoCambio()));

        return repo.save(nueva);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
