package exotic.app.planta.service.rehumanos;

import exotic.app.planta.model.organizacion.personal.RegistroVisitante;
import exotic.app.planta.model.organizacion.personal.RegistroVisitante.Motivo;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.Confirmacion;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.CrearRequest;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.Registro;
import exotic.app.planta.repo.personal.RegistroVisitanteRepo;
import exotic.app.planta.config.TimeZoneConfig;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistroVisitanteService {
    // Technical CC format used by SAT (Resolution 83/2021); no identity lookup.
    private static final Pattern CEDULA_PATTERN = Pattern.compile("[0-9]{3,10}");
    private static final Pattern NOMBRE_PATTERN = Pattern.compile("[\\p{L}\\p{M} '\u2019\u2010\u2011-]+");
    private static final Pattern LETRA_PATTERN = Pattern.compile("\\p{L}");
    private static final ZoneId ZONA_VISITAS = ZoneId.of(TimeZoneConfig.APP_TIME_ZONE_ID);
    private final RegistroVisitanteRepo repo;
    private final Clock applicationClock;

    @Transactional
    public Confirmacion registrar(CrearRequest request) {
        if (request == null) throw new IllegalArgumentException("Solicitud requerida.");
        String cedula = normalizarTexto(request.cedula(), 10);
        String nombreCompleto = normalizarTexto(request.nombreCompleto(), 160);
        if (!CEDULA_PATTERN.matcher(cedula).matches()) {
            throw new IllegalArgumentException("La cedula debe contener entre 3 y 10 digitos.");
        }
        if (!NOMBRE_PATTERN.matcher(nombreCompleto).matches() || !LETRA_PATTERN.matcher(nombreCompleto).find()) {
            throw new IllegalArgumentException("Nombre invalido.");
        }
        if (request.edad() != null && (request.edad() < 0 || request.edad() > 120)) {
            throw new IllegalArgumentException("Edad fuera del rango permitido.");
        }
        if (request.genero() != null && request.genero() != RegistroVisitante.Genero.MASCULINO
                && request.genero() != RegistroVisitante.Genero.FEMENINO) {
            throw new IllegalArgumentException("Genero invalido.");
        }

        if (request.motivo() == null) throw new IllegalArgumentException("Seleccione el motivo de la visita.");
        String motivoOtro = null;
        if (request.motivo() == Motivo.OTROS) {
            motivoOtro = normalizarTexto(request.motivoOtro() == null ? null
                    : request.motivoOtro().replaceAll("[\\t\\r\\n]+", " "), 300);
        } else if (request.motivoOtro() != null && !request.motivoOtro().isBlank()) {
            throw new IllegalArgumentException("El detalle solo corresponde al motivo Otros.");
        }

        RegistroVisitante registro = new RegistroVisitante(cedula, nombreCompleto, request.edad(),
                request.genero(), request.motivo(), motivoOtro, applicationClock.instant());
        RegistroVisitante guardado = repo.save(registro);
        return new Confirmacion(guardado.getFechaRegistro());
    }

    public Page<Registro> listar(int page, int size, LocalDate fecha, LocalTime horaDesde,
                                LocalTime horaHasta, String nombre, String orden) {
        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Paginacion fuera del rango permitido.");
        }
        if ((horaDesde != null && (horaDesde.getSecond() != 0 || horaDesde.getNano() != 0))
                || (horaHasta != null && (horaHasta.getSecond() != 0 || horaHasta.getNano() != 0))
                || (horaDesde != null && horaHasta != null && horaDesde.isAfter(horaHasta))) {
            throw new IllegalArgumentException("Rango de horas invalido.");
        }
        if (!"ASC".equals(orden) && !"DESC".equals(orden)) {
            throw new IllegalArgumentException("Orden invalido.");
        }
        LocalDate dia = fecha == null ? LocalDate.now(applicationClock.withZone(ZONA_VISITAS)) : fecha;
        Instant inicio = dia.atTime(horaDesde == null ? LocalTime.MIN : horaDesde).atZone(ZONA_VISITAS).toInstant();
        // The selected final minute is inclusive, including 23:59 at the end of the day.
        Instant fin = (horaHasta == null ? dia.plusDays(1).atStartOfDay() : dia.atTime(horaHasta).plusMinutes(1))
                .atZone(ZONA_VISITAS).toInstant();
        String nombreFiltro = nombre == null || nombre.isBlank() ? null : normalizarTexto(nombre, 160);
        String patronNombre = nombreFiltro == null ? null : "%" + nombreFiltro.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<RegistroVisitante> filtros = (root, query, cb) -> {
            var condiciones = new ArrayList<Predicate>();
            condiciones.add(cb.greaterThanOrEqualTo(root.<Instant>get("fechaRegistro"), inicio));
            condiciones.add(cb.lessThan(root.<Instant>get("fechaRegistro"), fin));
            if (patronNombre != null) {
                condiciones.add(cb.like(cb.lower(root.get("nombreCompleto")), patronNombre, '\\'));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        Sort ordenamiento = Sort.by(Sort.Direction.valueOf(orden), "fechaRegistro", "id");
        return repo.findAll(filtros, PageRequest.of(page, size, ordenamiento)).map(Registro::fromEntity);
    }

    private static String normalizarTexto(String valor, int maximo) {
        if (valor == null || valor.codePoints().anyMatch(codePoint -> {
            int tipo = Character.getType(codePoint);
            return tipo == Character.CONTROL || tipo == Character.FORMAT;
        })) {
            throw new IllegalArgumentException("Texto invalido.");
        }
        String normalizado = Normalizer.normalize(valor, Normalizer.Form.NFC)
                .replaceAll("\\p{Zs}+", " ").strip();
        if (normalizado.isBlank() || normalizado.codePointCount(0, normalizado.length()) > maximo) {
            throw new IllegalArgumentException("Longitud de texto invalida.");
        }
        return normalizado;
    }
}
