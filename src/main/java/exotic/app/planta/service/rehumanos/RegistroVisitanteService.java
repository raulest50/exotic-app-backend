package exotic.app.planta.service.rehumanos;

import exotic.app.planta.model.organizacion.personal.RegistroVisitante;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.Confirmacion;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.CrearRequest;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.Registro;
import exotic.app.planta.repo.personal.RegistroVisitanteRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistroVisitanteService {
    private final RegistroVisitanteRepo repo;
    private final Clock applicationClock;

    @Transactional
    public Confirmacion registrar(CrearRequest request) {
        if (request == null) throw new IllegalArgumentException("Solicitud requerida.");
        String cedula = normalizarTexto(request.cedula(), 30);
        String nombreCompleto = normalizarTexto(request.nombreCompleto(), 160);
        if (request.edad() != null && (request.edad() < 0 || request.edad() > 120)) {
            throw new IllegalArgumentException("Edad fuera del rango permitido.");
        }

        RegistroVisitante registro = new RegistroVisitante(cedula, nombreCompleto, request.edad(),
                request.genero(), applicationClock.instant());
        RegistroVisitante guardado = repo.save(registro);
        return new Confirmacion(guardado.getFechaRegistro());
    }

    public Page<Registro> listar(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Paginacion fuera del rango permitido.");
        }
        Sort orden = Sort.by(Sort.Order.desc("fechaRegistro"), Sort.Order.desc("id"));
        return repo.findAll(PageRequest.of(page, size, orden)).map(Registro::fromEntity);
    }

    private static String normalizarTexto(String valor, int maximo) {
        if (valor == null || valor.codePoints().anyMatch(codePoint -> {
            int tipo = Character.getType(codePoint);
            return tipo == Character.CONTROL || tipo == Character.FORMAT;
        })) {
            throw new IllegalArgumentException("Texto invalido.");
        }
        String normalizado = valor.trim();
        if (normalizado.isBlank() || normalizado.codePointCount(0, normalizado.length()) > maximo) {
            throw new IllegalArgumentException("Longitud de texto invalida.");
        }
        return normalizado;
    }
}
