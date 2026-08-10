package exotic.app.planta.service.organigrama;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.organigrama.MisionVisionValor;
import exotic.app.planta.model.organigrama.MisionVisionVersion;
import exotic.app.planta.model.organigrama.dto.MisionVisionRestoreRequest;
import exotic.app.planta.model.organigrama.dto.MisionVisionValorRequest;
import exotic.app.planta.model.organigrama.dto.MisionVisionVersionRequest;
import exotic.app.planta.model.organigrama.dto.MisionVisionVersionResponse;
import exotic.app.planta.model.organigrama.dto.MisionVisionVersionSummaryResponse;
import exotic.app.planta.repo.organigrama.MisionVisionVersionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MisionVisionService {

    private static final int MAX_VALORES = 12;

    private final MisionVisionVersionRepo repo;
    private final MisionVisionHtmlSanitizer htmlSanitizer;

    @Transactional(readOnly = true)
    public MisionVisionVersionResponse getVigente() {
        return repo.findFirstByEstadoOrderByVersionDesc(MisionVisionVersion.Estado.VIGENTE)
                .map(MisionVisionVersionResponse::fromEntity)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe una version vigente de mision y vision configurada."
                ));
    }

    @Transactional(readOnly = true)
    public List<MisionVisionVersionSummaryResponse> getVersiones() {
        return repo.findAllByOrderByVersionDesc().stream()
                .map(MisionVisionVersionSummaryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public MisionVisionVersionResponse getVersion(Long id) {
        return MisionVisionVersionResponse.fromEntity(findVersion(id));
    }

    @Transactional
    public MisionVisionVersionResponse crearNuevaVersion(
            MisionVisionVersionRequest request,
            String username
    ) {
        ValidatedContent content = validateRequest(request);
        MisionVisionVersion vigente = lockAndValidateCurrent(request.getVersionBase());

        return createVersion(
                vigente,
                content.misionHtml(),
                content.visionHtml(),
                content.valores(),
                trim(request.getMotivoCambio()),
                username,
                null
        );
    }

    @Transactional
    public MisionVisionVersionResponse restaurarVersion(
            Long sourceId,
            MisionVisionRestoreRequest request,
            String username
    ) {
        MisionVisionVersion source = findVersion(sourceId);
        MisionVisionVersion vigente = lockAndValidateCurrent(request.getVersionBase());

        if (source.getId().equals(vigente.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La version vigente no necesita restaurarse."
            );
        }

        List<ValidatedValue> restoredValues = source.getValores().stream()
                .map(value -> new ValidatedValue(
                        trim(value.getTitulo()),
                        htmlSanitizer.sanitizeRequired(value.getDescripcionHtml(), "La descripcion del valor")
                ))
                .toList();

        return createVersion(
                vigente,
                htmlSanitizer.sanitizeRequired(source.getMisionHtml(), "La mision"),
                htmlSanitizer.sanitizeRequired(source.getVisionHtml(), "La vision"),
                restoredValues,
                trim(request.getMotivoCambio()),
                username,
                source
        );
    }

    private MisionVisionVersionResponse createVersion(
            MisionVisionVersion vigente,
            String misionHtml,
            String visionHtml,
            List<ValidatedValue> values,
            String motivoCambio,
            String username,
            MisionVisionVersion origenVersion
    ) {
        LocalDateTime now = AppTime.now();
        vigente.setEstado(MisionVisionVersion.Estado.RETIRADA);
        vigente.setVigenteHasta(now);
        repo.save(vigente);

        MisionVisionVersion nueva = new MisionVisionVersion();
        nueva.setVersion(repo.findMaxVersion() + 1);
        nueva.setEstado(MisionVisionVersion.Estado.VIGENTE);
        nueva.setMisionHtml(misionHtml);
        nueva.setVisionHtml(visionHtml);
        nueva.setVigenteDesde(now);
        nueva.setCreadoEn(now);
        nueva.setCreadoPor(trim(username));
        nueva.setMotivoCambio(motivoCambio);
        nueva.setOrigenVersion(origenVersion);

        for (int index = 0; index < values.size(); index++) {
            ValidatedValue value = values.get(index);
            MisionVisionValor valor = new MisionVisionValor();
            valor.setMisionVisionVersion(nueva);
            valor.setOrden(index);
            valor.setTitulo(value.titulo());
            valor.setDescripcionHtml(value.descripcionHtml());
            nueva.getValores().add(valor);
        }

        return MisionVisionVersionResponse.fromEntity(repo.save(nueva));
    }

    private ValidatedContent validateRequest(MisionVisionVersionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud no puede ser nula.");
        }
        if (request.getValores() == null || request.getValores().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe existir al menos un valor corporativo.");
        }
        if (request.getValores().size() > MAX_VALORES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Se permiten maximo " + MAX_VALORES + " valores corporativos."
            );
        }

        try {
            String misionHtml = htmlSanitizer.sanitizeRequired(request.getMisionHtml(), "La mision");
            String visionHtml = htmlSanitizer.sanitizeRequired(request.getVisionHtml(), "La vision");
            List<ValidatedValue> values = request.getValores().stream()
                    .map(this::validateValue)
                    .toList();
            return new ValidatedContent(misionHtml, visionHtml, values);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private ValidatedValue validateValue(MisionVisionValorRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Los valores corporativos no pueden contener elementos nulos.");
        }
        String titulo = trim(request.getTitulo());
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El titulo de cada valor es obligatorio.");
        }
        if (titulo.length() > 120) {
            throw new IllegalArgumentException("El titulo de cada valor admite maximo 120 caracteres.");
        }
        String descripcion = htmlSanitizer.sanitizeRequired(
                request.getDescripcionHtml(),
                "La descripcion del valor " + titulo
        );
        return new ValidatedValue(titulo, descripcion);
    }

    private MisionVisionVersion lockAndValidateCurrent(Integer versionBase) {
        MisionVisionVersion vigente = repo.findByEstadoForUpdate(MisionVisionVersion.Estado.VIGENTE)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe una version vigente de mision y vision configurada."
                ));

        if (versionBase == null || !versionBase.equals(vigente.getVersion())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La mision y vision fue modificada por otro usuario. Recargue la version vigente."
            );
        }
        return vigente;
    }

    private MisionVisionVersion findVersion(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No existe la version de mision y vision con id: " + id
                ));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record ValidatedContent(
            String misionHtml,
            String visionHtml,
            List<ValidatedValue> valores
    ) {
    }

    private record ValidatedValue(String titulo, String descripcionHtml) {
    }
}
