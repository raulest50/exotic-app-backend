package exotic.app.planta.service.rehumanos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.pdf.PdfReader;
import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.organigrama.Cargo;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.organizacion.personal.dto.PermisoPersonalDTOs.*;
import exotic.app.planta.model.organizacion.personal.permiso.*;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion;
import exotic.app.planta.repo.organigrama.CargoOrganigramaRepo;
import exotic.app.planta.repo.personal.*;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.users.FirmaVisualUsuarioService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class PermisoPersonalService {
    private static final int MAX_SOPORTE = 5 * 1024 * 1024;
    private static final int MAX_FIRMA = 1024 * 1024;
    private static final ObjectMapper CANONICAL_JSON = new ObjectMapper();
    private final SolicitudPermisoPersonalRepo solicitudes;
    private final FirmaPermisoPersonalRepo firmas;
    private final EventoPermisoPersonalRepo eventos;
    private final ConfiguracionPermisoPersonalRepo configuraciones;
    private final IntegrantePersonalRepo empleados;
    private final UserRepository usuarios;
    private final CargoOrganigramaRepo cargos;
    private final FirmaVisualUsuarioService firmaVisual;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Contexto contexto(User actor) {
        int nivel = requireNivel(actor, 1);
        List<String> impedimentos = new ArrayList<>();
        List<IntegrantePersonal> vinculados = empleados.findByUsuarioId(actor.getId());
        IntegrantePersonal empleado = vinculados.size() == 1 ? vinculados.getFirst() : null;
        if (vinculados.isEmpty()) impedimentos.add("Debe vincular su cuenta a un trabajador desde la configuración de permisos.");
        if (vinculados.size() > 1) impedimentos.add("La cuenta está vinculada a más de un trabajador. Un administrador debe corregir el vínculo.");
        if (empleado != null && empleado.getEstado() != IntegrantePersonal.Estado.ACTIVO)
            impedimentos.add("El trabajador vinculado no está activo.");
        User jefe = empleado == null ? null : jefeActual(empleado);
        if (empleado != null && !jefeValido(jefe, actor))
            impedimentos.add("Debe asignarse un jefe activo, distinto del solicitante y autorizado para decidir permisos.");
        return new Contexto(actor.getId(), nombre(actor), nivel, empleado == null ? null : empleadoDto(empleado),
                usuarioDto(jefe), impedimentos.isEmpty(), List.copyOf(impedimentos), nivel >= 2, nivel >= 3, nivel >= 4);
    }

    @Transactional(readOnly = true)
    public Configuracion configuracion(User actor) {
        requireNivel(actor, 4);
        List<ConfiguracionEmpleado> filas = empleados.findAll(Sort.by("apellidos", "nombres")).stream().map(e -> {
            ConfiguracionPermisoPersonal c = configuraciones.findById(e.getId()).orElse(null);
            return new ConfiguracionEmpleado(empleadoDto(e), c == null ? null : usuarioDto(c.getJefe()),
                    usuarioDto(jefeOrganigrama(e)), c == null ? null : c.getVersion());
        }).toList();
        List<Usuario> opciones = usuarios.findByEstado(1).stream().sorted(Comparator.comparing(PermisoPersonalService::nombre))
                .map(PermisoPersonalService::usuarioDto).toList();
        return new Configuracion(filas, opciones);
    }

    @Transactional
    public Configuracion configurar(Long empleadoId, ConfigurarRequest request, User actor) {
        requireNivel(actor, 4);
        if (request == null) throw invalido("Debe indicar la configuración.");
        String motivo = texto(request.motivo(), 1500, true, "el motivo del cambio");
        IntegrantePersonal empleado = empleados.findByIdForUpdate(empleadoId)
                .orElseThrow(() -> noEncontrado("Trabajador no encontrado."));
        ConfiguracionPermisoPersonal config = configuraciones.findById(empleadoId).orElse(null);
        Long versionActual = config == null ? null : config.getVersion();
        if (!Objects.equals(versionActual, request.version())) throw conflicto("La configuración cambió. Recargue antes de guardar.");
        Long anteriorUsuario = empleado.getUsuario() == null ? null : empleado.getUsuario().getId();
        Long anteriorJefe = config == null || config.getJefe() == null ? null : config.getJefe().getId();
        User vinculado = null;
        if (request.usuarioId() != null) {
            // Lock the target account: two administrators cannot assign it to different workers concurrently.
            vinculado = usuarios.findByIdForUpdate(request.usuarioId()).orElseThrow(() -> invalido("La cuenta no existe."));
            if (vinculado.getEstado() != 1) throw invalido("La cuenta vinculada debe estar activa.");
            if (empleado.getEstado() != IntegrantePersonal.Estado.ACTIVO) throw invalido("Solo se puede vincular un trabajador activo.");
            boolean duplicado = empleados.findByUsuarioId(vinculado.getId()).stream().anyMatch(e -> e.getId() != empleadoId);
            if (duplicado) throw conflicto("La cuenta ya está vinculada a otro trabajador. Corrija ese vínculo primero.");
        }
        User jefe = request.jefeUsuarioId() == null ? null : usuarios.findById(request.jefeUsuarioId())
                .orElseThrow(() -> invalido("El jefe no existe."));
        if (jefe != null && !jefeValido(jefe, vinculado)) throw invalido("El jefe debe estar activo, tener nivel 2 de Permisos y ser distinto del trabajador.");
        empleado.setUsuario(vinculado);
        empleados.saveAndFlush(empleado);
        if (config == null) {
            config = new ConfiguracionPermisoPersonal();
            config.setEmpleado(empleado);
        }
        config.setJefe(jefe);
        config.setActualizadoEn(AppTime.now());
        config.setActualizadoPor(actor);
        configuraciones.saveAndFlush(config);
        evento(null, empleadoId, actor, "CONFIGURACION", "Cuenta: " + anteriorUsuario + " → " + request.usuarioId()
                + ". Jefe explícito: " + anteriorJefe + " → " + request.jefeUsuarioId() + ". Motivo: " + motivo);
        return configuracion(actor);
    }

    @Transactional(readOnly = true)
    public Page<Resumen> listar(Scope scope, Estado estado, LocalDate desde, LocalDate hasta, String q, int page, int size, User actor) {
        int nivel = requireNivel(actor, 1);
        Scope alcance = scope == null ? Scope.MIAS : scope;
        if (alcance == Scope.TODAS && nivel < 3) throw prohibido("No tiene permiso para consultar todos los expedientes.");
        if (alcance == Scope.POR_REVISAR && nivel < 2) throw prohibido("No tiene permiso para revisar solicitudes.");
        validarFechas(desde, hasta);
        String busqueda = texto(q, 255, false, "la búsqueda");
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "id"));
        if (pageable.getOffset() > Integer.MAX_VALUE) throw invalido("La página solicitada está fuera del intervalo permitido.");

        // Project only the listing columns. Loading entities here would also materialize every 5 MiB attachment.
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<SolicitudPermisoPersonal> root = query.from(SolicitudPermisoPersonal.class);
        query.multiselect(root.get("id").alias("id"), root.get("version").alias("version"),
                root.get("estado").alias("estado"), root.get("fechaSolicitud").alias("fechaSolicitud"),
                root.get("empleadoNombre").alias("empleadoNombre"), root.get("empleado").get("id").alias("empleadoId"),
                root.get("jefeNombre").alias("jefeNombre"), root.get("fechaDesde").alias("fechaDesde"),
                root.get("fechaHasta").alias("fechaHasta"), root.get("cantidad").alias("cantidad"), root.get("unidad").alias("unidad"));
        query.where(filtrosLista(cb, root, alcance, estado, desde, hasta, busqueda, actor.getId()));
        query.orderBy(cb.desc(root.get("id")));
        List<Resumen> contenido = entityManager.createQuery(query).setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize()).getResultList().stream().map(t -> new Resumen(
                        t.get("id", Long.class), t.get("version", Long.class), folio(t.get("id", Long.class)),
                        t.get("estado", Estado.class), t.get("fechaSolicitud", LocalDate.class), t.get("empleadoNombre", String.class),
                        t.get("empleadoId", Long.class), t.get("jefeNombre", String.class), t.get("fechaDesde", LocalDate.class),
                        t.get("fechaHasta", LocalDate.class), t.get("cantidad", BigDecimal.class), t.get("unidad", Unidad.class))).toList();
        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<SolicitudPermisoPersonal> countRoot = count.from(SolicitudPermisoPersonal.class);
        count.select(cb.count(countRoot)).where(filtrosLista(cb, countRoot, alcance, estado, desde, hasta, busqueda, actor.getId()));
        return new PageImpl<>(contenido, pageable, entityManager.createQuery(count).getSingleResult());
    }

    private Predicate filtrosLista(CriteriaBuilder cb, Root<SolicitudPermisoPersonal> root, Scope alcance, Estado estado,
                                    LocalDate desde, LocalDate hasta, String busqueda, Long actorId) {
        List<Predicate> condiciones = new ArrayList<>();
        if (alcance == Scope.MIAS) condiciones.add(cb.equal(root.get("solicitante").get("id"), actorId));
        if (alcance == Scope.POR_REVISAR) {
            condiciones.add(cb.equal(root.get("jefe").get("id"), actorId));
            condiciones.add(cb.equal(root.get("estado"), Estado.SOLICITADA));
        }
        if (estado != null) condiciones.add(cb.equal(root.get("estado"), estado));
        if (desde != null) condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaHasta"), desde));
        if (hasta != null) condiciones.add(cb.lessThanOrEqualTo(root.get("fechaDesde"), hasta));
        if (busqueda != null) condiciones.add(cb.or(
                cb.like(cb.lower(root.get("empleadoNombre")), "%" + busqueda.toLowerCase(Locale.ROOT) + "%"),
                cb.like(root.get("empleado").get("id").as(String.class), "%" + busqueda + "%")));
        return cb.and(condiciones.toArray(Predicate[]::new));
    }

    @Transactional
    public Detalle crear(Borrador request, User actor) {
        requireNivel(actor, 1);
        Contexto contexto = contexto(actor);
        if (!contexto.habilitado()) throw conflicto(String.join(" ", contexto.impedimentos()));
        IntegrantePersonal empleado = requireEmpleado(actor);
        SolicitudPermisoPersonal s = new SolicitudPermisoPersonal();
        s.setEmpleado(empleado);
        s.setSolicitante(actor);
        s.setSolicitanteUsername(actor.getUsername());
        s.setEstado(Estado.BORRADOR);
        s.setFechaSolicitud(AppTime.now().toLocalDate());
        s.setCreadoEn(AppTime.now());
        s.setActualizadoEn(s.getCreadoEn());
        refrescarIdentidad(s, empleado);
        aplicar(s, request);
        solicitudes.saveAndFlush(s);
        evento(s, null, actor, "CREADA", "Borrador creado por el trabajador solicitante.");
        return detalleDto(s, actor);
    }

    @Transactional(readOnly = true)
    public Detalle detalle(Long id, User actor) {
        SolicitudPermisoPersonal s = buscar(id);
        requireLectura(s, actor);
        return detalleDto(s, actor);
    }

    @Transactional
    public Detalle guardar(Long id, Borrador request, User actor) {
        SolicitudPermisoPersonal s = paraCambio(id, request == null ? null : request.version(), actor);
        requireBorradorPropio(s, actor);
        IntegrantePersonal empleado = requireEmpleado(actor);
        if (empleado.getId() != s.getEmpleado().getId()) throw prohibido("La cuenta ya no está vinculada al trabajador del borrador.");
        refrescarIdentidad(s, empleado);
        aplicar(s, request);
        guardarCambio(s);
        evento(s, null, actor, "EDITADA", "Contenido del borrador actualizado.");
        return detalleDto(s, actor);
    }

    @Transactional
    public Detalle presentar(Long id, PresentarRequest request, User actor) {
        SolicitudPermisoPersonal s = paraCambio(id, request == null ? null : request.version(), actor);
        requireBorradorPropio(s, actor);
        IntegrantePersonal empleado = requireEmpleado(actor);
        if (empleado.getId() != s.getEmpleado().getId()) throw prohibido("La cuenta ya no está vinculada al trabajador del borrador.");
        // The signature covers exactly the version displayed to the worker. Changes in personnel/configuration
        // require another explicit save and review; never substitute identities during the signing operation.
        User jefeActual = jefeActual(empleado);
        if (!Objects.equals(s.getEmpleadoNombre(), nombre(empleado))
                || !Objects.equals(s.getEmpleadoCargo(), texto(empleado.getCargo(), 255, false, "el cargo"))
                || !Objects.equals(s.getJefe() == null ? null : s.getJefe().getId(), jefeActual == null ? null : jefeActual.getId())
                || !Objects.equals(s.getJefeNombre(), jefeActual == null ? null : nombre(jefeActual))
                || !Objects.equals(s.getJefeUsername(), jefeActual == null ? null : jefeActual.getUsername()))
            throw conflicto("La ficha del trabajador o su jefe cambió. Guarde el borrador y revise la versión actualizada antes de firmar.");
        if (!jefeValido(s.getJefe(), actor)) throw conflicto("Debe configurarse un jefe activo y autorizado antes de presentar.");
        validarCompleto(s);
        s.setHashContenido(hashContenido(s));
        // Persist the canonical hash before inserting signature evidence (Hibernate may otherwise INSERT first).
        solicitudes.saveAndFlush(s);
        firmar(s, actor, "SOLICITANTE", request.confirmacion(), request.imagenBase64(), request.firmaVisualVersionId());
        s.setEstado(Estado.SOLICITADA);
        s.setPresentadoEn(AppTime.now());
        guardarCambio(s);
        evento(s, null, actor, "PRESENTADA", "Solicitud firmada y presentada al jefe asignado. SHA-256: " + s.getHashContenido());
        return detalleDto(s, actor);
    }

    @Transactional
    public Detalle decidir(Long id, DecidirRequest request, User actor) {
        requireNivel(actor, 2);
        SolicitudPermisoPersonal s = paraCambio(id, request == null ? null : request.version(), actor);
        if (!puedeDecidir(s, actor)) throw prohibido("Solo el jefe asignado, distinto del solicitante, puede resolver una solicitud pendiente.");
        if (request.decision() == null) throw invalido("Debe indicar la decisión.");
        verificarIntegridad(s);
        String motivo = texto(request.motivo(), 2000, request.decision() == Decision.RECHAZAR, "el motivo de la decisión");
        if (request.decision() == Decision.APROBAR) {
            firmar(s, actor, "JEFE", request.confirmacion(), request.imagenBase64(), request.firmaVisualVersionId());
            s.setEstado(Estado.APROBADA);
        } else {
            s.setEstado(Estado.RECHAZADA);
        }
        s.setMotivoDecision(motivo);
        s.setResueltoEn(AppTime.now());
        guardarCambio(s);
        evento(s, null, actor, s.getEstado().name(), motivo == null ? "Permiso firmado y aprobado." : motivo);
        return detalleDto(s, actor);
    }

    @Transactional
    public Detalle anular(Long id, AnularRequest request, User actor) {
        SolicitudPermisoPersonal s = paraCambio(id, request == null ? null : request.version(), actor);
        if (!puedeAnular(s, actor)) throw prohibido("Solo puede retirar una solicitud propia pendiente o anularla con nivel administrativo.");
        if (s.getHashContenido() != null) verificarIntegridad(s);
        String motivo = texto(request.motivo(), 2000, true, "el motivo de anulación");
        s.setEstado(Estado.ANULADA);
        s.setMotivoAnulacion(motivo);
        guardarCambio(s);
        evento(s, null, actor, "ANULADA", motivo);
        return detalleDto(s, actor);
    }

    @Transactional
    public Detalle guardarSoporte(Long id, Long version, MultipartFile archivo, User actor) {
        SolicitudPermisoPersonal s = paraCambio(id, version, actor);
        requireBorradorPropio(s, actor);
        if (archivo == null || archivo.isEmpty()) throw invalido("Debe seleccionar un soporte PDF, PNG o JPEG.");
        if (archivo.getSize() > MAX_SOPORTE) throw invalido("El soporte no puede superar 5 MiB.");
        byte[] bytes;
        try { bytes = archivo.getBytes(); } catch (IOException e) { throw invalido("No se pudo leer el soporte."); }
        if (bytes.length > MAX_SOPORTE) throw invalido("El soporte no puede superar 5 MiB.");
        String tipo = inspeccionarSoporte(bytes);
        String declarado = archivo.getContentType();
        if (declarado != null && !declarado.equals("application/octet-stream") && !declarado.equalsIgnoreCase(tipo))
            throw invalido("El tipo de archivo declarado no coincide con el contenido.");
        s.setSoporteNombre(nombreArchivo(archivo.getOriginalFilename(), tipo));
        s.setSoporteContentType(tipo);
        s.setSoporteContenido(bytes);
        s.setSoporteSha256(sha256(bytes));
        guardarCambio(s);
        evento(s, null, actor, "SOPORTE_ADJUNTADO", "Soporte " + s.getSoporteNombre() + ". SHA-256: " + s.getSoporteSha256());
        return detalleDto(s, actor);
    }

    @Transactional
    public Detalle borrarSoporte(Long id, Long version, User actor) {
        SolicitudPermisoPersonal s = paraCambio(id, version, actor);
        requireBorradorPropio(s, actor);
        s.setSoporteNombre(null); s.setSoporteContentType(null); s.setSoporteContenido(null); s.setSoporteSha256(null);
        guardarCambio(s);
        evento(s, null, actor, "SOPORTE_RETIRADO", "Soporte retirado del borrador.");
        return detalleDto(s, actor);
    }

    @Transactional(readOnly = true)
    public Archivo soporte(Long id, User actor) {
        SolicitudPermisoPersonal s = buscar(id);
        requireLectura(s, actor);
        if (s.getSoporteContenido() == null) throw noEncontrado("La solicitud no tiene soporte adjunto.");
        if (!sha256(s.getSoporteContenido()).equals(s.getSoporteSha256())) throw conflicto("El soporte no supera la comprobación de integridad.");
        return new Archivo(s.getSoporteNombre(), s.getSoporteContentType(), s.getSoporteContenido().clone());
    }

    @Transactional(readOnly = true)
    public FirmaVigente firmaVigente(User actor) {
        requireNivel(actor, 1);
        try {
            FirmaVisualUsuarioVersion vigente = firmaVisual.getVigente(actor.getId());
            return new FirmaVigente(vigente.getId(), Base64.getEncoder().encodeToString(vigente.getContenido()), vigente.getSha256());
        } catch (NoSuchElementException e) { return null; }
    }

    @Transactional(readOnly = true)
    public DocumentoPdf documentoPdf(Long id, User actor) {
        SolicitudPermisoPersonal s = buscar(id);
        requireLectura(s, actor);
        if (s.getEstado() == Estado.BORRADOR) throw conflicto("Presente la solicitud antes de generar el documento histórico.");
        if (s.getHashContenido() != null) verificarIntegridad(s);
        List<FirmaPermisoPersonal> evidencia = firmas.findBySolicitudIdOrderByIdAsc(id);
        byte[] solicitante = evidencia.stream().filter(f -> f.getRol().equals("SOLICITANTE"))
                .findFirst().map(f -> f.getContenido().clone()).orElse(null);
        byte[] jefe = evidencia.stream().filter(f -> f.getRol().equals("JEFE"))
                .findFirst().map(f -> f.getContenido().clone()).orElse(null);
        return new DocumentoPdf(detalleDto(s, actor), solicitante, jefe);
    }

    private void aplicar(SolicitudPermisoPersonal s, Borrador r) {
        if (r == null) throw invalido("Debe indicar el contenido del borrador.");
        s.setProceso(texto(r.proceso(), 255, false, "el proceso"));
        s.setMotivo(r.motivo());
        s.setDescripcion(texto(r.descripcion(), 10000, false, "la descripción"));
        s.setDocumentoSoporte(texto(r.documentoSoporte(), 1000, false, "el documento soporte"));
        s.setMecanismoRecuperacion(texto(r.mecanismoRecuperacion(), 2000, false, "el mecanismo de recuperación"));
        validarFechas(r.fechaDesde(), r.fechaHasta());
        s.setFechaDesde(r.fechaDesde()); s.setFechaHasta(r.fechaHasta());
        if (r.cantidad() != null && (r.cantidad().signum() <= 0 || r.cantidad().compareTo(new BigDecimal("9999999999.99")) > 0
                || r.cantidad().stripTrailingZeros().scale() > 2))
            throw invalido("La cantidad debe ser positiva, con hasta dos decimales.");
        s.setCantidad(r.cantidad()); s.setUnidad(r.unidad());
    }

    private void validarCompleto(SolicitudPermisoPersonal s) {
        texto(s.getProceso(), 255, true, "el proceso");
        texto(s.getEmpleadoCargo(), 255, true, "el cargo del trabajador");
        texto(s.getDescripcion(), 10000, true, "la descripción del permiso");
        if (s.getMotivo() == null || s.getFechaDesde() == null || s.getFechaHasta() == null || s.getCantidad() == null || s.getUnidad() == null)
            throw invalido("Complete motivo, fechas, cantidad y unidad antes de presentar.");
        validarFechas(s.getFechaDesde(), s.getFechaHasta());
    }

    private void refrescarIdentidad(SolicitudPermisoPersonal s, IntegrantePersonal empleado) {
        s.setEmpleadoNombre(nombre(empleado));
        s.setEmpleadoCargo(texto(empleado.getCargo(), 255, false, "el cargo"));
        User jefe = jefeActual(empleado);
        if (jefe != null && jefe.getId().equals(s.getSolicitante().getId())) jefe = null;
        s.setJefe(jefe);
        s.setJefeNombre(jefe == null ? null : nombre(jefe));
        s.setJefeUsername(jefe == null ? null : jefe.getUsername());
    }

    private IntegrantePersonal requireEmpleado(User actor) {
        List<IntegrantePersonal> lista = empleados.findByUsuarioId(actor.getId());
        if (lista.size() != 1 || lista.getFirst().getEstado() != IntegrantePersonal.Estado.ACTIVO)
            throw conflicto("La cuenta debe estar vinculada de forma única a un trabajador activo.");
        return lista.getFirst();
    }

    private User jefeActual(IntegrantePersonal empleado) {
        return configuraciones.findById(empleado.getId()).map(ConfiguracionPermisoPersonal::getJefe)
                .orElseGet(() -> jefeOrganigrama(empleado));
    }

    private User jefeOrganigrama(IntegrantePersonal empleado) {
        if (empleado.getUsuario() == null) return null;
        List<Cargo> propios = cargos.findAll().stream().filter(c -> c.getUsuario() != null
                && c.getUsuario().getId().equals(empleado.getUsuario().getId())).toList();
        if (propios.size() != 1 || propios.getFirst().getJefeInmediato() == null) return null;
        return cargos.findById(propios.getFirst().getJefeInmediato()).map(Cargo::getUsuario).orElse(null);
    }

    private boolean jefeValido(User jefe, User solicitante) {
        return jefe != null && jefe.getEstado() == 1 && nivel(jefe) >= 2
                && (solicitante == null || !jefe.getId().equals(solicitante.getId()));
    }

    private SolicitudPermisoPersonal buscar(Long id) {
        return solicitudes.findById(id).orElseThrow(() -> noEncontrado("Solicitud de permiso no encontrada."));
    }

    private SolicitudPermisoPersonal paraCambio(Long id, Long version, User actor) {
        requireNivel(actor, 1);
        SolicitudPermisoPersonal s = solicitudes.findByIdForUpdate(id).orElseThrow(() -> noEncontrado("Solicitud de permiso no encontrada."));
        requireLectura(s, actor);
        if (version == null || !Objects.equals(s.getVersion(), version)) throw conflicto("La solicitud cambió. Recargue el expediente antes de continuar.");
        return s;
    }

    private void guardarCambio(SolicitudPermisoPersonal s) {
        s.setActualizadoEn(AppTime.now());
        solicitudes.saveAndFlush(s);
    }

    private void requireBorradorPropio(SolicitudPermisoPersonal s, User actor) {
        if (s.getEstado() != Estado.BORRADOR) throw conflicto("El contenido presentado y sus soportes ya no pueden modificarse.");
        if (!esPropia(s, actor)) throw prohibido("Solo el solicitante puede editar o firmar su borrador.");
    }

    private void requireLectura(SolicitudPermisoPersonal s, User actor) {
        int nivel = requireNivel(actor, 1);
        boolean asignada = s.getJefe() != null && s.getJefe().getId().equals(actor.getId()) && nivel >= 2 && s.getEstado() != Estado.BORRADOR;
        if (!esPropia(s, actor) && !asignada && nivel < 3) throw prohibido("No tiene acceso a este expediente de permiso.");
    }

    private boolean esPropia(SolicitudPermisoPersonal s, User actor) { return s.getSolicitante().getId().equals(actor.getId()); }
    private boolean puedeDecidir(SolicitudPermisoPersonal s, User actor) {
        return nivel(actor) >= 2 && s.getEstado() == Estado.SOLICITADA && !esPropia(s, actor)
                && s.getJefe() != null && s.getJefe().getId().equals(actor.getId());
    }
    private boolean puedeAnular(SolicitudPermisoPersonal s, User actor) {
        return s.getEstado() != Estado.ANULADA && (nivel(actor) >= 4 || (esPropia(s, actor)
                && (s.getEstado() == Estado.BORRADOR || s.getEstado() == Estado.SOLICITADA)));
    }

    private int requireNivel(User actor, int requerido) {
        if (actor == null || actor.getId() == null) throw new ResponseStatusException(UNAUTHORIZED, "No autenticado.");
        if (actor.getEstado() != 1 && !"master".equalsIgnoreCase(actor.getUsername()))
            throw prohibido("La cuenta no está activa. Un administrador debe revisar su estado para acceder a Permisos.");
        int nivel = nivel(actor);
        if (nivel < requerido) throw prohibido("No tiene el nivel requerido en PERSONAL / Permisos.");
        return nivel;
    }
    private static int nivel(User actor) {
        if (actor == null) return 0;
        if ("master".equalsIgnoreCase(actor.getUsername())) return 4;
        if (actor.getEstado() != 1) return 0;
        if ("super_master".equalsIgnoreCase(actor.getUsername())) return 4;
        return UserAccessEvaluator.tabNivel(actor, ModuloSistema.PERSONAL_PLANTA, "PERMISOS").orElse(0);
    }

    private void firmar(SolicitudPermisoPersonal s, User actor, String rol, Boolean confirmacion, String imagen, Long vigenteId) {
        if (!Boolean.TRUE.equals(confirmacion)) throw invalido("Debe confirmar expresamente que firma la versión mostrada.");
        boolean hayImagen = imagen != null && !imagen.isBlank();
        if (hayImagen == (vigenteId != null)) throw invalido("Seleccione una única firma: imagen propia o firma visual vigente.");
        byte[] bytes;
        if (vigenteId != null) {
            FirmaVisualUsuarioVersion vigente;
            try { vigente = firmaVisual.getVigente(actor.getId()); }
            catch (NoSuchElementException e) { throw conflicto("La cuenta no tiene una firma visual vigente."); }
            if (!vigente.getId().equals(vigenteId)) throw conflicto("La firma visual seleccionada cambió o fue retirada. Selecciónela nuevamente.");
            bytes = normalizarFirma(vigente.getContenido());
        } else {
            if (imagen.length() > MAX_FIRMA * 4L / 3L + 100) throw invalido("La firma no puede superar 1 MiB.");
            String codificada = imagen.startsWith("data:image/png;base64,") ? imagen.substring("data:image/png;base64,".length()) : imagen;
            try { bytes = Base64.getDecoder().decode(codificada); }
            catch (IllegalArgumentException e) { throw invalido("La firma no contiene una imagen Base64 válida."); }
            bytes = normalizarFirma(bytes);
        }
        FirmaPermisoPersonal firma = new FirmaPermisoPersonal();
        firma.setSolicitud(s); firma.setFirmante(actor); firma.setRol(rol);
        firma.setUsername(actor.getUsername()); firma.setNombre(nombre(actor)); firma.setCedula(Long.toString(actor.getCedula()));
        firma.setFirmadoEn(AppTime.now()); firma.setHashContenido(s.getHashContenido());
        firma.setContenido(bytes); firma.setSha256(sha256(bytes)); firma.setFirmaVisualVersionId(vigenteId);
        firmas.saveAndFlush(firma);
    }

    private byte[] normalizarFirma(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FIRMA) throw invalido("La firma debe ser PNG o JPEG de hasta 1 MiB.");
        BufferedImage source = leerImagenAcotada(bytes, 4096, 4_000_000L);
        if (source.getWidth() < 50 || source.getHeight() < 20) throw invalido("La firma debe medir al menos 50 por 20 píxeles.");
        boolean trazo = false;
        int base = source.getRGB(0, 0);
        for (int y = 0; y < source.getHeight() && !trazo; y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int pixel = source.getRGB(x, y);
                // Compare visible appearance on white, so fully transparent arbitrary RGB values cannot impersonate a signature.
                if (distanciaVisual(base, pixel) > 30) { trazo = true; break; }
            }
        }
        if (!trazo) { source.flush(); throw invalido("La firma está vacía o no tiene trazos distinguibles."); }
        double escala = Math.min(1.0, Math.min(1200.0 / source.getWidth(), 600.0 / source.getHeight()));
        int width = (int) Math.round(source.getWidth() * escala), height = (int) Math.round(source.getHeight() * escala);
        if (width < 50 || height < 20) { source.flush(); throw invalido("La firma tiene una proporción demasiado estrecha para conservar sus trazos."); }
        BufferedImage normalized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally { graphics.dispose(); source.flush(); }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(normalized, "png", out)) throw invalido("No se pudo normalizar la firma.");
            byte[] result = out.toByteArray();
            if (result.length > MAX_FIRMA) throw invalido("La firma normalizada supera 1 MiB.");
            return result;
        } catch (IOException e) { throw invalido("No se pudo leer la firma."); }
        finally { normalized.flush(); }
    }

    private static int distanciaVisual(int a, int b) {
        int resultado = 0;
        for (int shift : new int[]{0, 8, 16}) {
            int aa = (a >>> 24) & 255, ba = (b >>> 24) & 255;
            int ac = (((a >>> shift) & 255) * aa + 255 * (255 - aa)) / 255;
            int bc = (((b >>> shift) & 255) * ba + 255 * (255 - ba)) / 255;
            resultado += Math.abs(ac - bc);
        }
        return resultado;
    }

    private BufferedImage leerImagenAcotada(byte[] bytes, int maxSide, long maxPixels) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw invalido("El archivo no es una imagen PNG o JPEG válida.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format)) throw invalido("Solo se permiten imágenes PNG o JPEG.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > maxSide || height > maxSide || (long) width * height > maxPixels)
                    throw invalido("La imagen supera las dimensiones permitidas.");
                BufferedImage image = reader.read(0);
                if (image == null) throw invalido("No se pudo decodificar la imagen.");
                return image;
            } finally { reader.dispose(); }
        } catch (IOException e) { throw invalido("La imagen está dañada o no es compatible."); }
    }

    private String inspeccionarSoporte(byte[] bytes) {
        if (bytes.length > 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') {
            PdfReader reader = null;
            try {
                reader = new PdfReader(bytes);
                if (reader.isEncrypted() || reader.getNumberOfPages() < 1) throw invalido("El PDF debe ser legible y no estar cifrado.");
                return "application/pdf";
            } catch (IOException e) { throw invalido("El PDF está dañado o protegido."); }
            finally { if (reader != null) reader.close(); }
        }
        BufferedImage image = leerImagenAcotada(bytes, 8192, 20_000_000L);
        image.flush();
        return bytes.length >= 8 && (bytes[0] & 255) == 137 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                ? "image/png" : "image/jpeg";
    }

    private String hashContenido(SolicitudPermisoPersonal s) {
        // Fixed field order and schema version provide one canonical representation for both signatures.
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("schema", "permiso-personal-v1"); contenido.put("id", s.getId());
        contenido.put("empleadoId", s.getEmpleado().getId()); contenido.put("empleadoNombre", s.getEmpleadoNombre());
        contenido.put("empleadoCargo", s.getEmpleadoCargo()); contenido.put("solicitanteId", s.getSolicitante().getId());
        contenido.put("solicitanteUsername", s.getSolicitanteUsername()); contenido.put("jefeId", s.getJefe() == null ? null : s.getJefe().getId());
        contenido.put("jefeNombre", s.getJefeNombre()); contenido.put("jefeUsername", s.getJefeUsername());
        contenido.put("fechaSolicitud", s.getFechaSolicitud().toString()); contenido.put("proceso", s.getProceso());
        contenido.put("motivo", s.getMotivo() == null ? null : s.getMotivo().name()); contenido.put("descripcion", s.getDescripcion());
        contenido.put("documentoSoporte", s.getDocumentoSoporte()); contenido.put("mecanismoRecuperacion", s.getMecanismoRecuperacion());
        contenido.put("fechaDesde", s.getFechaDesde() == null ? null : s.getFechaDesde().toString());
        contenido.put("fechaHasta", s.getFechaHasta() == null ? null : s.getFechaHasta().toString());
        contenido.put("cantidad", s.getCantidad() == null ? null : s.getCantidad().stripTrailingZeros().toPlainString());
        contenido.put("unidad", s.getUnidad() == null ? null : s.getUnidad().name());
        contenido.put("soporteNombre", s.getSoporteNombre()); contenido.put("soporteContentType", s.getSoporteContentType());
        contenido.put("soporteSha256", s.getSoporteSha256());
        try { return sha256(CANONICAL_JSON.writeValueAsBytes(contenido)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("No se pudo identificar la revisión documental.", e); }
    }

    private void verificarIntegridad(SolicitudPermisoPersonal s) {
        if (s.getHashContenido() == null || !s.getHashContenido().equals(hashContenido(s)))
            throw conflicto("El contenido no coincide con la versión firmada. Se requiere revisión administrativa.");
        if (s.getSoporteContenido() != null && !sha256(s.getSoporteContenido()).equals(s.getSoporteSha256()))
            throw conflicto("El soporte no coincide con el contenido presentado.");
        List<FirmaPermisoPersonal> evidencia = firmas.findBySolicitudIdOrderByIdAsc(s.getId());
        if (evidencia.stream().noneMatch(f -> "SOLICITANTE".equals(f.getRol()) && f.getFirmante().getId().equals(s.getSolicitante().getId())))
            throw conflicto("Falta la firma del solicitante sobre la revisión presentada.");
        boolean aprobadaAntes = s.getEstado() == Estado.APROBADA || s.getEstado() == Estado.ANULADA
                && eventos.findBySolicitudIdOrderByIdAsc(s.getId()).stream().anyMatch(e -> "APROBADA".equals(e.getTipo()));
        if (aprobadaAntes
                && evidencia.stream().noneMatch(f -> "JEFE".equals(f.getRol()) && s.getJefe() != null && f.getFirmante().getId().equals(s.getJefe().getId())))
            throw conflicto("Falta la firma del jefe asignado sobre la revisión aprobada.");
        for (FirmaPermisoPersonal f : evidencia) {
            if (!s.getHashContenido().equals(f.getHashContenido()) || !sha256(f.getContenido()).equals(f.getSha256()))
                throw conflicto("Una firma no coincide con su evidencia histórica.");
        }
    }

    private void evento(SolicitudPermisoPersonal s, Long empleadoId, User actor, String tipo, String detalle) {
        EventoPermisoPersonal e = new EventoPermisoPersonal();
        e.setSolicitud(s); e.setEmpleadoId(s == null ? empleadoId : s.getEmpleado().getId()); e.setActor(actor);
        e.setActorNombre(nombre(actor)); e.setTipo(tipo); e.setDetalle(detalle); e.setCreadoEn(AppTime.now()); eventos.saveAndFlush(e);
    }

    private Detalle detalleDto(SolicitudPermisoPersonal s, User actor) {
        boolean propia = esPropia(s, actor), borrador = s.getEstado() == Estado.BORRADOR;
        Empleado empleado = new Empleado(s.getEmpleado().getId(), s.getEmpleadoNombre(), s.getEmpleadoCargo(), s.getProceso(), s.getSolicitante().getId());
        Usuario jefe = s.getJefe() == null ? null : new Usuario(s.getJefe().getId(), s.getJefeUsername(), s.getJefeNombre());
        Soporte soporte = s.getSoporteContenido() == null ? null : new Soporte(s.getSoporteNombre(), s.getSoporteContentType(), s.getSoporteContenido().length, s.getSoporteSha256());
        List<Firma> evidencia = firmas.findBySolicitudIdOrderByIdAsc(s.getId()).stream().map(f -> new Firma(f.getRol(), f.getFirmante().getId(),
                f.getUsername(), f.getNombre(), f.getCedula(), f.getFirmadoEn(), f.getHashContenido(), f.getSha256(), Base64.getEncoder().encodeToString(f.getContenido()))).toList();
        List<Evento> historia = eventos.findBySolicitudIdOrderByIdAsc(s.getId()).stream().map(e -> new Evento(e.getId(), e.getTipo(), e.getActor().getId(),
                e.getActorNombre(), e.getCreadoEn(), e.getDetalle())).toList();
        return new Detalle(s.getId(), s.getVersion(), folio(s), s.getEstado(), s.getFechaSolicitud(), empleado, jefe, s.getProceso(), s.getMotivo(),
                s.getDescripcion(), s.getDocumentoSoporte(), s.getMecanismoRecuperacion(), s.getFechaDesde(), s.getFechaHasta(), s.getCantidad(), s.getUnidad(),
                soporte, s.getHashContenido(), s.getCreadoEn(), s.getPresentadoEn(), s.getResueltoEn(), s.getMotivoDecision(), s.getMotivoAnulacion(),
                evidencia, historia, propia && borrador, propia && borrador, puedeDecidir(s, actor), puedeAnular(s, actor));
    }

    private static Empleado empleadoDto(IntegrantePersonal e) {
        return new Empleado(e.getId(), nombre(e), e.getCargo(), e.getDepartamento() == null ? null : e.getDepartamento().name(), e.getUsuario() == null ? null : e.getUsuario().getId());
    }
    private static Usuario usuarioDto(User u) { return u == null ? null : new Usuario(u.getId(), u.getUsername(), nombre(u)); }
    private static String nombre(User u) { return u.getNombreCompleto() == null || u.getNombreCompleto().isBlank() ? u.getUsername() : u.getNombreCompleto().trim(); }
    private static String nombre(IntegrantePersonal e) { return (e.getNombres() + " " + e.getApellidos()).trim(); }
    private static String folio(SolicitudPermisoPersonal s) { return folio(s.getId()); }
    private static String folio(Long id) { return "PER-" + String.format(Locale.ROOT, "%06d", id); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void validarFechas(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) throw invalido("La fecha final no puede ser anterior a la inicial.");
    }
    private static String texto(String value, int max, boolean requerido, String campo) {
        String result = value == null ? null : value.trim();
        if (result != null && result.isBlank()) result = null;
        if (requerido && result == null) throw invalido("Debe indicar " + campo + ".");
        if (result != null && result.length() > max) throw invalido("El campo " + campo + " supera " + max + " caracteres.");
        return result;
    }
    private static String nombreArchivo(String original, String type) {
        String limpio = original == null ? "soporte" : original.replace('\\', '/');
        limpio = limpio.substring(limpio.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (limpio.isBlank()) limpio = "soporte";
        String extension = type.equals("application/pdf") ? ".pdf" : type.equals("image/png") ? ".png" : ".jpg";
        int punto = limpio.lastIndexOf('.');
        if (punto > 0) limpio = limpio.substring(0, punto);
        return limpio.substring(0, Math.min(limpio.length(), 240)) + extension;
    }
    private static ResponseStatusException invalido(String m) { return new ResponseStatusException(BAD_REQUEST, m); }
    private static ResponseStatusException conflicto(String m) { return new ResponseStatusException(CONFLICT, m); }
    private static ResponseStatusException prohibido(String m) { return new ResponseStatusException(FORBIDDEN, m); }
    private static ResponseStatusException noEncontrado(String m) { return new ResponseStatusException(NOT_FOUND, m); }
}
