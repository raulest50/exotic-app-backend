package exotic.app.planta.service.rehumanos;

import exotic.app.planta.model.organizacion.personal.RegistroVisitante;
import exotic.app.planta.model.organizacion.personal.RegistroVisitante.Estado;
import exotic.app.planta.model.organizacion.personal.RegistroVisitante.Motivo;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.Confirmacion;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.CrearRequest;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.Registro;
import exotic.app.planta.model.organizacion.personal.dto.VisitanteDTOs.RegistrarSalidaRequest;
import exotic.app.planta.repo.personal.RegistroVisitanteRepo;
import exotic.app.planta.config.TimeZoneConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.text.Normalizer;
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
    private final FirmaVisitanteService firmaService;
    private final VisitantesPrivacidadService privacidadService;

    @Transactional
    public Confirmacion registrar(CrearRequest request) {
        if (request == null) throw new IllegalArgumentException("Solicitud requerida.");
        var aviso = privacidadService.exigirAceptacion(request.aceptaDatosPersonales(), request.consentimientoVersion());
        String cedula = normalizarTexto(request.cedula(), 10);
        String nombreCompleto = normalizarTexto(request.nombreCompleto(), 160);
        if (!CEDULA_PATTERN.matcher(cedula).matches()) {
            throw new IllegalArgumentException("La cedula debe contener entre 3 y 10 digitos.");
        }
        if (!NOMBRE_PATTERN.matcher(nombreCompleto).matches() || !LETRA_PATTERN.matcher(nombreCompleto).find()) {
            throw new IllegalArgumentException("Nombre invalido.");
        }
        if (request.edad() != null && (request.edad() < 18 || request.edad() > 120)) {
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

        if (request.tipoIngreso() == null) throw new IllegalArgumentException("Tipo de ingreso requerido.");
        String cargoEmpresa = request.cargoEmpresa() == null || request.cargoEmpresa().isBlank()
                ? null : normalizarTexto(request.cargoEmpresa(), 160);
        String areaPersonaVisita = normalizarTexto(request.areaPersonaVisita(), 160);
        var firma = firmaService.validar(request.firmaBase64(), request.autorizaFirma());

        RegistroVisitante registro = new RegistroVisitante(cedula, nombreCompleto, request.edad(),
                request.genero(), request.motivo(), motivoOtro, cargoEmpresa, request.tipoIngreso(), areaPersonaVisita,
                aviso.version(), aviso.textoAceptado(), firma == null ? null : firma.contenido(),
                firma == null ? null : firma.sha256(), firma == null ? null : aviso.autorizacionFirma(), applicationClock.instant());
        RegistroVisitante guardado = repo.save(registro);
        return new Confirmacion(guardado.getFechaRegistro());
    }

    public Page<Registro> listar(int page, int size, LocalDate fecha, LocalTime horaDesde,
                                LocalTime horaHasta, String nombre) {
        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Paginacion fuera del rango permitido.");
        }
        if ((horaDesde != null && (horaDesde.getSecond() != 0 || horaDesde.getNano() != 0))
                || (horaHasta != null && (horaHasta.getSecond() != 0 || horaHasta.getNano() != 0))
                || (horaDesde != null && horaHasta != null && horaDesde.isAfter(horaHasta))) {
            throw new IllegalArgumentException("Rango de horas invalido.");
        }
        LocalDate dia = fecha == null ? LocalDate.now(applicationClock.withZone(ZONA_VISITAS)) : fecha;
        Instant inicio = dia.atTime(horaDesde == null ? LocalTime.MIN : horaDesde).atZone(ZONA_VISITAS).toInstant();
        // The selected final minute is inclusive, including 23:59 at the end of the day.
        Instant fin = (horaHasta == null ? dia.plusDays(1).atStartOfDay() : dia.atTime(horaHasta).plusMinutes(1))
                .atZone(ZONA_VISITAS).toInstant();
        String nombreFiltro = nombre == null || nombre.isBlank() ? null : normalizarTexto(nombre, 160);
        String patronNombre = nombreFiltro == null ? null : "%" + nombreFiltro.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return repo.consultar(inicio, fin, patronNombre, Estado.PENDIENTE_SALIDA, Estado.SALIO,
                PageRequest.of(page, size)).map(RegistroVisitanteService::aRegistro);
    }

    @Transactional
    public Registro registrarSalida(Long id, RegistrarSalidaRequest request, Long actorId, String actorUsername) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Registro invalido.");
        RegistroVisitanteRepo.Resumen actual = exigirResumen(id);
        // Retrying a completed visit always returns the first committed closing evidence.
        if (actual.getEstado() == Estado.SALIO) return aRegistro(actual);
        if (actual.getEstado() != Estado.PENDIENTE_SALIDA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este registro historico no tiene seguimiento de salida.");
        }
        Instant fechaSalida;
        try {
            if (request == null || request.fechaSalida() == null) throw new IllegalArgumentException();
            fechaSalida = OffsetDateTime.parse(request.fechaSalida()).toInstant();
        } catch (DateTimeParseException | IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de salida debe ser texto ISO 8601 con offset, por ejemplo 2026-11-25T15:30:00-05:00.");
        }
        Instant registradaEn = applicationClock.instant();
        if (fechaSalida.isBefore(actual.getFechaRegistro()) || fechaSalida.isAfter(registradaEn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La salida debe ser igual o posterior al ingreso y no superar la hora actual del servidor.");
        }
        if (actorId == null || actorId <= 0 || actorUsername == null || actorUsername.isBlank()
                || actorUsername.length() > 255) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no disponible.");
        }
        repo.registrarSalida(id, fechaSalida, registradaEn, actorId, actorUsername,
                Estado.PENDIENTE_SALIDA, Estado.SALIO);
        RegistroVisitanteRepo.Resumen resultado = exigirResumen(id);
        if (resultado.getEstado() != Estado.SALIO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No fue posible registrar la salida de esta visita.");
        }
        return aRegistro(resultado);
    }

    private RegistroVisitanteRepo.Resumen exigirResumen(Long id) {
        return repo.obtenerResumen(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el registro de visita."));
    }

    private static Registro aRegistro(RegistroVisitanteRepo.Resumen r) {
        return new Registro(r.getId(), r.getCedula(), r.getNombreCompleto(), r.getEdad(), r.getGenero(),
                r.getMotivo(), r.getMotivoOtro(), r.getFechaRegistro(), r.getCargoEmpresa(), r.getTipoIngreso(),
                r.getAreaPersonaVisita(), r.getFirmaRegistrada(), r.getEstado(), r.getFechaSalida(),
                r.getSalidaRegistradaEn(), r.getSalidaRegistradaPorId(), r.getSalidaRegistradaPorUsername());
    }

    public byte[] obtenerFirma(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Registro invalido.");
        return repo.obtenerFirma(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
