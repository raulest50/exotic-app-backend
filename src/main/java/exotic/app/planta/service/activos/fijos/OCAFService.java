package exotic.app.planta.service.activos.fijos;

import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import exotic.app.planta.model.activos.fijos.compras.ItemOrdenCompraActivo;
import exotic.app.planta.model.activos.fijos.compras.OrdenCompraActivo;
import exotic.app.planta.model.activos.fijos.dto.UpdateEstadoOrdenCompraAFRequest;
import exotic.app.planta.model.compras.ContactoProveedor;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.activos.fijos.ItemOrdenCompraActivoRepo;
import exotic.app.planta.repo.activos.fijos.OrdenCompraActivoRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.commons.EmailService;
import exotic.app.planta.service.commons.FileStorageService;
import exotic.app.planta.service.empresa.EmpresaIdentidadDocumentalService;
import exotic.app.planta.service.empresa.EmpresaIdentidadLegalService;
import exotic.app.planta.service.empresa.EmpresaLogoDocumentalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para la gestión de órdenes de compra de activos fijos (OCAF).
 * Proporciona métodos para crear, actualizar, buscar y procesar órdenes de compra.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OCAFService {

    private final OrdenCompraActivoRepo ordenCompraActivoRepo;
    private final ItemOrdenCompraActivoRepo itemOrdenCompraActivoRepo;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final EmpresaIdentidadDocumentalService empresaIdentidadDocumentalService;
    private final EmpresaIdentidadLegalService empresaIdentidadLegalService;
    private final EmpresaLogoDocumentalService empresaLogoDocumentalService;

    /**
     * Guarda una nueva orden de compra de activos fijos con un archivo de cotización opcional.
     *
     * @param ordenCompraActivo la orden de compra a guardar
     * @param cotizacionFile archivo de cotización opcional
     * @return la orden de compra guardada con su ID asignado
     * @throws IOException si ocurre un error al guardar el archivo
     */
    @Transactional
    public OrdenCompraActivo saveOrdenCompraActivo(OrdenCompraActivo ordenCompraActivo, MultipartFile cotizacionFile) throws IOException {
        log.info("Guardando orden de compra de activos fijos");

        // Validar que el ID de la orden no exista ya
        if (ordenCompraActivo.getOrdenCompraActivoId() != 0 && 
            ordenCompraActivoRepo.existsById(ordenCompraActivo.getOrdenCompraActivoId())) {
            throw new IllegalArgumentException("Ya existe una orden de compra con el ID: " + 
                                              ordenCompraActivo.getOrdenCompraActivoId());
        }

        // Validar que los IDs de los ítems no existan ya
        if (ordenCompraActivo.getItemsOrdenCompra() != null && !ordenCompraActivo.getItemsOrdenCompra().isEmpty()) {
            for (var item : ordenCompraActivo.getItemsOrdenCompra()) {
                if (item.getItemOrdenId() != 0 && itemOrdenCompraActivoRepo.existsById(item.getItemOrdenId())) {
                    throw new IllegalArgumentException("Ya existe un ítem de orden de compra con el ID: " + 
                                                      item.getItemOrdenId());
                }
            }
        }

        // Validaciones básicas
        if (ordenCompraActivo.getProveedor() == null) {
            throw new IllegalArgumentException("La orden de compra debe tener un proveedor asignado");
        }

        if (ordenCompraActivo.getFechaVencimiento() == null) {
            throw new IllegalArgumentException("La fecha de vencimiento es requerida");
        }

        // Toda OCA nueva inicia pendiente de liberación.
        ordenCompraActivo.setEstado(0);

        // Procesar los ítems de la orden
        if (ordenCompraActivo.getItemsOrdenCompra() != null && !ordenCompraActivo.getItemsOrdenCompra().isEmpty()) {
            double subtotal = 0;
            double ivaTotal = 0;

            for (var item : ordenCompraActivo.getItemsOrdenCompra()) {
                // Establecer la relación bidireccional
                item.setOrdenCompraActivo(ordenCompraActivo);

                // Calcular subtotal del ítem si no está establecido
                if (item.getSubTotal() == 0) {
                    item.setSubTotal(item.getPrecioUnitario() * item.getCantidad());
                }

                // Acumular totales
                subtotal += item.getSubTotal();
                ivaTotal += item.getIvaValue() * item.getCantidad();
            }

            // Actualizar totales de la orden
            ordenCompraActivo.setSubTotal(subtotal);
            ordenCompraActivo.setIva(ivaTotal);
            ordenCompraActivo.setTotalPagar(subtotal + ivaTotal);
        } else {
            // Calcular totales si no hay ítems pero están establecidos manualmente
            if (ordenCompraActivo.getTotalPagar() == 0) {
                double subtotal = ordenCompraActivo.getSubTotal();
                double iva = ordenCompraActivo.getIva();
                ordenCompraActivo.setTotalPagar(subtotal + iva);
            }
        }

        // Set cotizacionUrl to empty string by default
        ordenCompraActivo.setCotizacionUrl("");
        // El snapshot documental solo puede fijarse durante la transición 1 -> 2.
        ordenCompraActivo.setEmpresaIdentidadLegalVersion(null);
        ordenCompraActivo.setEmpresaLogoDocumentalVersion(null);

        // Save the entity first to get the ID
        OrdenCompraActivo savedOrden = ordenCompraActivoRepo.save(ordenCompraActivo);

        // If a quotation file is provided, store it and update the URL
        if (cotizacionFile != null && !cotizacionFile.isEmpty()) {
            String cotizacionPath = fileStorageService.storeCotizacionFile(savedOrden.getOrdenCompraActivoId(), cotizacionFile);
            savedOrden.setCotizacionUrl(cotizacionPath);
            // Save again to update the URL
            savedOrden = ordenCompraActivoRepo.save(savedOrden);
        }

        return savedOrden;
    }

    /**
     * Guarda una nueva orden de compra de activos fijos.
     * Este método mantiene la compatibilidad con el código existente.
     *
     * @param ordenCompraActivo la orden de compra a guardar
     * @return la orden de compra guardada con su ID asignado
     */
    @Transactional
    public OrdenCompraActivo saveOrdenCompraActivo(OrdenCompraActivo ordenCompraActivo) {
        try {
            return saveOrdenCompraActivo(ordenCompraActivo, null);
        } catch (IOException e) {
            // This should never happen since we're not passing a file
            throw new RuntimeException("Unexpected error saving orden compra activo", e);
        }
    }

    /**
     * Busca órdenes de compra por rango de fechas y estados.
     *
     * @param date1 fecha inicial en formato yyyy-MM-dd
     * @param date2 fecha final en formato yyyy-MM-dd
     * @param estados cadena de estados separados por coma (ej: "0,1,2")
     * @param page número de página (0-indexed)
     * @param size tamaño de página
     * @return página de órdenes de compra que cumplen con los criterios
     */
    public Page<OrdenCompraActivo> getOrdenesCompraByDateAndEstado(
            String date1, String date2, String estados, int page, int size) {

        // Convertir fechas de String a LocalDateTime
        LocalDateTime startDate = LocalDate.parse(date1).atTime(LocalTime.MIN);
        LocalDateTime endDate = LocalDate.parse(date2).atTime(LocalTime.MAX);

        // Convertir estados de String a List<Integer>
        List<Integer> estadosList = Arrays.stream(estados.split(","))
                .map(Integer::parseInt)
                .collect(Collectors.toList());

        Pageable pageable = PageRequest.of(page, size);

        return ordenCompraActivoRepo.findByFechaEmisionBetweenAndEstadoIn(
                startDate, endDate, estadosList, pageable);
    }

    /**
     * Obtiene una orden de compra por su ID y estado.
     *
     * @param ordenCompraActivoId ID de la orden de compra
     * @param estado estado de la orden
     * @return la orden de compra si existe
     * @throws RuntimeException si no se encuentra la orden
     */
    public OrdenCompraActivo getOrdenCompraByIdAndEstado(Integer ordenCompraActivoId, int estado) {
        return ordenCompraActivoRepo.findByOrdenCompraActivoIdAndEstado(ordenCompraActivoId, estado)
                .orElseThrow(() -> new RuntimeException("Orden de compra no encontrada con ID: " + 
                        ordenCompraActivoId + " y estado: " + estado));
    }

    /**
     * Cancela una orden de compra de activos fijos.
     *
     * @param ordenCompraActivoId ID de la orden de compra a cancelar
     * @return la orden de compra actualizada
     * @throws RuntimeException si no se encuentra la orden
     */
    @Transactional
    public OrdenCompraActivo cancelOrdenCompraActivo(int ordenCompraActivoId) {
        OrdenCompraActivo ordenCompra = ordenCompraActivoRepo.findById(ordenCompraActivoId)
                .orElseThrow(() -> new RuntimeException("Orden de compra no encontrada con ID: " + ordenCompraActivoId));

        // Verificar si la orden ya está cancelada
        if (ordenCompra.getEstado() == -1) {
            throw new RuntimeException("La orden de compra ya está cancelada");
        }

        // Verificar si la orden está en un estado que permite cancelación
        if (ordenCompra.getEstado() > 1) {
            throw new RuntimeException("No se puede cancelar una orden que ya está en proceso de envío o recepción");
        }

        // Cancelar la orden
        ordenCompra.setEstado(-1);
        return ordenCompraActivoRepo.save(ordenCompra);
    }

    /**
     * Obtiene los ítems de una orden de compra específica.
     *
     * @param ordenCompraActivoId ID de la orden de compra
     * @return lista de ítems de la orden
     */
    public List<ItemOrdenCompraActivo> getItemsByOrdenCompraId(int ordenCompraActivoId) {
        return itemOrdenCompraActivoRepo.findByOrdenCompraActivo_OrdenCompraActivoId(ordenCompraActivoId);
    }

    /**
     * Actualiza una orden de compra de activos fijos existente con un archivo de cotización opcional.
     * Verifica primero si la orden existe antes de intentar actualizarla.
     *
     * @param ordenCompraActivo la orden de compra con los datos actualizados
     * @param cotizacionFile archivo de cotización opcional
     * @return la orden de compra actualizada
     * @throws RuntimeException si la orden no existe o no se puede actualizar
     * @throws IOException si ocurre un error al guardar el archivo
     */
    @Transactional
    public OrdenCompraActivo updateOrdenCompraActivo(OrdenCompraActivo ordenCompraActivo, MultipartFile cotizacionFile) throws IOException {
        log.info("Actualizando orden de compra de activos fijos con ID: {}", ordenCompraActivo.getOrdenCompraActivoId());

        // Verificar que la orden exista
        OrdenCompraActivo existingOrden = ordenCompraActivoRepo.findById(ordenCompraActivo.getOrdenCompraActivoId())
                .orElseThrow(() -> new RuntimeException("Orden de compra no encontrada con ID: " + 
                        ordenCompraActivo.getOrdenCompraActivoId()));

        // Verificar si la orden está en un estado que permite modificación
        if (existingOrden.getEstado() == -1) {
            throw new RuntimeException("No se puede modificar una orden cancelada");
        }

        if (existingOrden.getEstado() > 1) {
            throw new RuntimeException("No se puede modificar una orden que ya está en proceso de envío o recepción");
        }

        // Validaciones básicas
        if (ordenCompraActivo.getProveedor() == null) {
            throw new IllegalArgumentException("La orden de compra debe tener un proveedor asignado");
        }

        if (ordenCompraActivo.getFechaVencimiento() == null) {
            throw new IllegalArgumentException("La fecha de vencimiento es requerida");
        }

        // Mantener la fecha de emisión original
        ordenCompraActivo.setFechaEmision(existingOrden.getFechaEmision());
        // Estos campos no son editables desde el payload general de la OCA.
        ordenCompraActivo.setEmpresaIdentidadLegalVersion(existingOrden.getEmpresaIdentidadLegalVersion());
        ordenCompraActivo.setEmpresaLogoDocumentalVersion(existingOrden.getEmpresaLogoDocumentalVersion());

        // Mantener la URL de cotización original si no se proporciona un nuevo archivo
        if (cotizacionFile == null || cotizacionFile.isEmpty()) {
            ordenCompraActivo.setCotizacionUrl(existingOrden.getCotizacionUrl());
        }

        // Procesar los ítems de la orden
        if (ordenCompraActivo.getItemsOrdenCompra() != null && !ordenCompraActivo.getItemsOrdenCompra().isEmpty()) {
            double subtotal = 0;
            double ivaTotal = 0;

            for (var item : ordenCompraActivo.getItemsOrdenCompra()) {
                // Establecer la relación bidireccional
                item.setOrdenCompraActivo(ordenCompraActivo);

                // Calcular subtotal del ítem si no está establecido
                if (item.getSubTotal() == 0) {
                    item.setSubTotal(item.getPrecioUnitario() * item.getCantidad());
                }

                // Acumular totales
                subtotal += item.getSubTotal();
                ivaTotal += item.getIvaValue() * item.getCantidad();
            }

            // Actualizar totales de la orden
            ordenCompraActivo.setSubTotal(subtotal);
            ordenCompraActivo.setIva(ivaTotal);
            ordenCompraActivo.setTotalPagar(subtotal + ivaTotal);
        } else {
            // Calcular totales si no hay ítems pero están establecidos manualmente
            if (ordenCompraActivo.getTotalPagar() == 0) {
                double subtotal = ordenCompraActivo.getSubTotal();
                double iva = ordenCompraActivo.getIva();
                ordenCompraActivo.setTotalPagar(subtotal + iva);
            }
        }

        // Eliminar los ítems existentes asociados a esta orden
        itemOrdenCompraActivoRepo.deleteByOrdenCompraActivo_OrdenCompraActivoId(ordenCompraActivo.getOrdenCompraActivoId());

        // Guardar la orden actualizada
        OrdenCompraActivo updatedOrden = ordenCompraActivoRepo.save(ordenCompraActivo);

        // Si se proporciona un nuevo archivo de cotización, guardarlo y actualizar la URL
        if (cotizacionFile != null && !cotizacionFile.isEmpty()) {
            String cotizacionPath = fileStorageService.storeCotizacionFile(updatedOrden.getOrdenCompraActivoId(), cotizacionFile);
            updatedOrden.setCotizacionUrl(cotizacionPath);
            updatedOrden = ordenCompraActivoRepo.save(updatedOrden);
        }

        return updatedOrden;
    }

    /**
     * Actualiza una orden de compra de activos fijos existente.
     * Este método mantiene la compatibilidad con el código existente.
     *
     * @param ordenCompraActivo la orden de compra con los datos actualizados
     * @return la orden de compra actualizada
     * @throws RuntimeException si la orden no existe o no se puede actualizar
     */
    @Transactional
    public OrdenCompraActivo updateOrdenCompraActivo(OrdenCompraActivo ordenCompraActivo) {
        try {
            return updateOrdenCompraActivo(ordenCompraActivo, null);
        } catch (IOException e) {
            // This should never happen since we're not passing a file
            throw new RuntimeException("Unexpected error updating orden compra activo", e);
        }
    }
    
    /**
     * Envía un correo electrónico al proveedor con la orden de compra de activo fijo adjunta.
     * 
     * @param orden La orden de compra a enviar
     * @param pdfAttachment El archivo PDF adjunto con los detalles de la orden
     * @throws MessagingException Si hay un error al enviar el correo
     * @throws IOException Si hay un error al procesar el archivo adjunto
     * @throws RuntimeException Si no se encuentra un email para el proveedor
     */
    public void enviarCorreoOrdenCompraActivoProveedor(OrdenCompraActivo orden, MultipartFile pdfAttachment) 
            throws MessagingException, IOException {

        if (pdfAttachment == null || pdfAttachment.isEmpty()) {
            throw new RuntimeException("No se proporcionó archivo PDF para enviar por email para la orden: " + orden.getOrdenCompraActivoId());
        }

        // Obtener el proveedor y su información de contacto
        Proveedor proveedor = orden.getProveedor();

        // Buscar el email del proveedor en la lista de contactos
        String emailProveedor = null;
        for (ContactoProveedor contacto : proveedor.getContactos()) {
            if (contacto.getEmail() != null && !contacto.getEmail().isBlank()) {
                emailProveedor = contacto.getEmail();
                break;
            }
        }

        if (emailProveedor == null) {
            throw new RuntimeException("No se encontró email para el proveedor con ID: " + proveedor.getId());
        }

        String subject = buildEmailSubject(orden);
        String text = buildEmailBody(orden);

        // Enviar el correo con el PDF adjunto
        emailService.sendEmailWithAttachment(
                emailProveedor,
                subject,
                text,
                pdfAttachment
        );

        log.info("Email sent to provider {} with order details for order ID: {}", emailProveedor, orden.getOrdenCompraActivoId());
    }

    /**
     * Envía un correo electrónico al proveedor con copia a otros destinatarios.
     * Funciona igual que {@link #enviarCorreoOrdenCompraActivoProveedor(OrdenCompraActivo, MultipartFile)}
     * pero permite especificar destinatarios en copia (CC).
     *
     * @param orden       La orden de compra a enviar
     * @param pdfAttachment El archivo PDF adjunto con los detalles de la orden
     * @param ccEmails    Lista de correos electrónicos que recibirán copia
     * @throws MessagingException Si hay un error al enviar el correo
     * @throws IOException        Si hay un error al procesar el archivo adjunto
     * @throws RuntimeException   Si no se encuentra un email para el proveedor
     */
    public void enviarCorreoOrdenCompraActivoProveedor_wCC(OrdenCompraActivo orden, MultipartFile pdfAttachment, List<String> ccEmails)
            throws MessagingException, IOException {

        if (pdfAttachment == null || pdfAttachment.isEmpty()) {
            throw new RuntimeException("No se proporcionó archivo PDF para enviar por email para la orden: " + orden.getOrdenCompraActivoId());
        }

        // Obtener el proveedor y su información de contacto
        Proveedor proveedor = orden.getProveedor();

        // Buscar el email del proveedor en la lista de contactos
        String emailProveedor = null;
        for (ContactoProveedor contacto : proveedor.getContactos()) {
            if (contacto.getEmail() != null && !contacto.getEmail().isBlank()) {
                emailProveedor = contacto.getEmail();
                break;
            }
        }

        if (emailProveedor == null) {
            throw new RuntimeException("No se encontró email para el proveedor con ID: " + proveedor.getId());
        }

        String subject = buildEmailSubject(orden);
        String text = buildEmailBody(orden);

        emailService.sendEmailWithAttachmentAndCC(
                emailProveedor,
                ccEmails.toArray(new String[0]),
                subject,
                text,
                pdfAttachment
        );

        log.info("Email sent to provider {} with CC {} for order ID: {}", emailProveedor, ccEmails, orden.getOrdenCompraActivoId());
    }

    /**
     * Obtiene los correos electrónicos de los usuarios con acceso al módulo PRODUCCION
     * y nivel 2.
     *
     * @return lista de correos electrónicos
     */
    public List<String> getEmailsUsuariosProduccionNivel2() {
        return userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && !u.getEmail().isEmpty())
                .filter(u -> UserAccessEvaluator.hasModuloWithTabNivel(u, ModuloSistema.PRODUCCION, 2))
                .map(User::getEmail)
                .collect(Collectors.toList());
    }

    /**
     * Actualiza el estado de una orden de compra de activos fijos.
     * Si el nuevo estado es 2 (pendiente recepción) y el estado actual es 1 (pendiente envío a proveedor),
     * se envía un correo electrónico al proveedor según el tipo de envío seleccionado.
     *
     * @param ordenCompraActivoId ID de la orden de compra a actualizar
     * @param request Objeto con la información de actualización
     * @return La orden de compra actualizada
     * @throws RuntimeException Si la orden no existe o hay algún error en la actualización
     */
    @Transactional
    public OrdenCompraActivo updateEstadoOrdenCompraActivo(int ordenCompraActivoId, UpdateEstadoOrdenCompraAFRequest request) {
        OrdenCompraActivo orden = ordenCompraActivoRepo.findById(ordenCompraActivoId)
                .orElseThrow(() -> new RuntimeException("Orden de compra de activo fijo no encontrada con id: " + ordenCompraActivoId));

        validateEstadoTransition(orden.getEstado(), request.getNewEstado());
        boolean transicionEnvioProveedor = request.getNewEstado() == 2 && orden.getEstado() == 1;
        if (transicionEnvioProveedor) {
            if (request.getTipoEnvio() == null) {
                throw new IllegalArgumentException("Debe seleccionar el tipo de envío de la OCA.");
            }

            resolveDocumentalSnapshot(orden, request);

            switch (request.getTipoEnvio()) {
                case MANUAL:
                    log.info("Orden de compra de activo fijo {} actualizada manualmente a estado 2", ordenCompraActivoId);
                    break;

                case EMAIL:
                    try {
                        List<String> ccEmails = getEmailsUsuariosProduccionNivel2();
                        if (ccEmails.isEmpty()) {
                            enviarCorreoOrdenCompraActivoProveedor(orden, request.getOCAFpdf());
                        } else {
                            enviarCorreoOrdenCompraActivoProveedor_wCC(orden, request.getOCAFpdf(), ccEmails);
                        }
                    } catch (MessagingException | IOException e) {
                        log.error("Error al enviar correo al proveedor: {}", e.getMessage(), e);
                        throw new RuntimeException("Error al enviar correo al proveedor: " + e.getMessage(), e);
                    } catch (RuntimeException e) {
                        log.error(e.getMessage());
                        throw e;
                    }
                    break;

                case WHATSAPP:
                    log.info("Envío por WhatsApp seleccionado para orden {}, esta funcionalidad será implementada próximamente", ordenCompraActivoId);
                    throw new UnsupportedOperationException("El envío por WhatsApp aún no está implementado");

                default:
                    throw new IllegalArgumentException("Tipo de envío no reconocido para la orden: " + ordenCompraActivoId);
            }
        }

        // Actualizar el estado solo después de que todo el proceso haya sido exitoso
        orden.setEstado(request.getNewEstado());
        return ordenCompraActivoRepo.save(orden);
    }

    private void validateEstadoTransition(int estadoActual, int nuevoEstado) {
        if (nuevoEstado == 1 && estadoActual != 0) {
            throw new IllegalStateException(
                    "Solo se puede liberar una OCA que esté pendiente de liberación."
            );
        }
        if (nuevoEstado == 2 && estadoActual != 1) {
            throw new IllegalStateException(
                    "Solo se puede enviar una OCA que esté pendiente de envío al proveedor."
            );
        }
        if (nuevoEstado != 1 && nuevoEstado != 2) {
            throw new IllegalArgumentException("El cambio de estado solicitado no está permitido para la OCA.");
        }
    }

    private void resolveDocumentalSnapshot(
            OrdenCompraActivo orden,
            UpdateEstadoOrdenCompraAFRequest request
    ) {
        EmpresaIdentidadLegalVersion identidadExistente = orden.getEmpresaIdentidadLegalVersion();
        EmpresaLogoDocumentalVersion logoExistente = orden.getEmpresaLogoDocumentalVersion();

        if (identidadExistente != null || logoExistente != null) {
            if (identidadExistente == null || logoExistente == null) {
                throw new IllegalStateException("La OCA tiene una asociación documental histórica incompleta.");
            }
            validateRequestedSnapshotMatchesExisting(identidadExistente, logoExistente, request);
            return;
        }

        Long identidadId = request.getEmpresaIdentidadLegalVersionId();
        Long logoId = request.getEmpresaLogoDocumentalVersionId();
        if ((identidadId == null) != (logoId == null)) {
            throw new IllegalArgumentException(
                    "Las versiones de identidad legal y logo documental deben enviarse juntas."
            );
        }

        if (identidadId == null) {
            var vigente = empresaIdentidadDocumentalService.getVigente();
            identidadId = vigente.identidadLegal().id();
            logoId = vigente.logo().id();
        }

        orden.setEmpresaIdentidadLegalVersion(empresaIdentidadLegalService.resolveVersion(identidadId));
        orden.setEmpresaLogoDocumentalVersion(empresaLogoDocumentalService.resolveVersion(logoId));
    }

    private void validateRequestedSnapshotMatchesExisting(
            EmpresaIdentidadLegalVersion identidadExistente,
            EmpresaLogoDocumentalVersion logoExistente,
            UpdateEstadoOrdenCompraAFRequest request
    ) {
        Long identidadId = request.getEmpresaIdentidadLegalVersionId();
        Long logoId = request.getEmpresaLogoDocumentalVersionId();
        if ((identidadId == null) != (logoId == null)) {
            throw new IllegalArgumentException(
                    "Las versiones de identidad legal y logo documental deben enviarse juntas."
            );
        }
        if (identidadId != null
                && (!identidadExistente.getId().equals(identidadId) || !logoExistente.getId().equals(logoId))) {
            throw new IllegalArgumentException("La OCA ya tiene una identidad documental diferente asociada.");
        }
    }

    private String buildEmailSubject(OrdenCompraActivo orden) {
        return "No Reply - Orden de Compra de Activo Fijo "
                + getNombreComercialDocumento(orden)
                + " #"
                + orden.getOrdenCompraActivoId();
    }

    private String buildEmailBody(OrdenCompraActivo orden) {
        return "Estimado proveedor: " + orden.getProveedor().getNombre() + ",\n\n"
                + "Por medio de la presente le hacemos llegar la orden de compra de activo fijo #"
                + orden.getOrdenCompraActivoId()
                + " correspondiente a los productos/servicios detallados en el documento adjunto.\n"
                + "Le agradeceremos confirmar la recepción de esta orden y, en caso de ser necesario, "
                + "informarnos sobre el tiempo estimado de entrega o cualquier observación relevante.\n\n"
                + "Quedamos atentos a su confirmación y agradecemos de antemano su atención y colaboración.\n\n"
                + "Saludos cordiales,\n"
                + getNombreComercialDocumento(orden)
                + " - Departamento de Compras";
    }

    private String getNombreComercialDocumento(OrdenCompraActivo orden) {
        EmpresaIdentidadLegalVersion identidad = orden.getEmpresaIdentidadLegalVersion();
        if (identidad == null || identidad.getNombreComercial() == null || identidad.getNombreComercial().isBlank()) {
            throw new IllegalStateException("La OCA no tiene una identidad legal válida asociada.");
        }
        return identidad.getNombreComercial().trim();
    }
}
