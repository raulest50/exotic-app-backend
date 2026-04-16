package exotic.app.planta.service.compras;

import exotic.app.planta.model.compras.*;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import exotic.app.planta.model.compras.dto.UpdateEstadoOrdenCompraRequest;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.compras.FacturaCompraRepo;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.service.commons.EmailService;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
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
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ComprasService {

    private final FacturaCompraRepo facturaCompraRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final ProveedorRepo proveedorRepo;
    private final MaterialRepo materialRepo;
    private final SemiTerminadoRepo semiTerminadoRepo;
    private final TerminadoRepo terminadoRepo;

    private final OrdenCompraRepo ordenCompraRepo;

    private final EmailService emailService;

    private final UserRepository userRepository;

    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;

    /**
     *
     * Compras
     *
     */



    public Page<FacturaCompra> getComprasByProveedorAndDate(String proveedorId, String date1, String date2, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime startDate = LocalDate.parse(date1).atStartOfDay();
        LocalDateTime endDate = LocalDate.parse(date2).atTime(LocalTime.MAX);
        return facturaCompraRepo.findByProveedorIdAndFechaCompraBetween(proveedorId, startDate, endDate, pageable);
    }

    public List<ItemFacturaCompra> getItemsByCompraId(int compraId) {
        FacturaCompra facturaCompra = facturaCompraRepo.findById(compraId)
                .orElseThrow(() -> new RuntimeException("Compra not found with id: " + compraId));
        return facturaCompra.getItemsCompra();
    }


    /**
     *
     * Ordenes de Compra
     *
     */
    public OrdenCompraMateriales saveOrdenCompra(OrdenCompraMateriales ordenCompraMateriales) {
        logProveedorPayloadAnomaly("saveOrdenCompra", ordenCompraMateriales);

        if (ordenCompraMateriales == null || ordenCompraMateriales.getProveedor() == null || isBlank(ordenCompraMateriales.getProveedor().getId())) {
            throw new IllegalArgumentException("La orden de compra debe incluir un proveedor valido.");
        }

        // Verify the Proveedor exists
        Optional<Proveedor> optProveedor = proveedorRepo.findById(ordenCompraMateriales.getProveedor().getId().trim());
        if (!optProveedor.isPresent()) {
            throw new RuntimeException("Proveedor not found with ID: " + ordenCompraMateriales.getProveedor().getId());
        }
        ordenCompraMateriales.setProveedor(optProveedor.get());

        // For each ItemOrdenCompra, set the back‑reference and initialize check fields to 0
        for (ItemOrdenCompra item : ordenCompraMateriales.getItemsOrdenCompra()) {
            item.setOrdenCompraMateriales(ordenCompraMateriales);
            if(item.getCantidadCorrecta() == 0) item.setCantidadCorrecta(0);
            if(item.getPrecioCorrecto() == 0) item.setPrecioCorrecto(0);
        }

        // Save and return the OrdenCompraMateriales
        return ordenCompraRepo.save(ordenCompraMateriales);
    }

    public Page<OrdenCompraMateriales> getOrdenesCompraByDateAndEstado(String date1, String date2, String estados, int page, int size,
                                                                       String proveedorId) {
        // Parse the date strings
        LocalDateTime startDate = LocalDate.parse(date1).atStartOfDay();
        LocalDateTime endDate = LocalDate.parse(date2).atTime(LocalTime.MAX);

        // Parse the estados string into a list of integers
        List<Integer> estadoList = Arrays.stream(estados.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toList());

        Pageable pageable = PageRequest.of(page, size);
        String proveedorIdFilter = (proveedorId != null && !proveedorId.isBlank()) ? proveedorId : null;

        if (proveedorIdFilter != null) {
            return ordenCompraRepo.findByFechaEmisionBetweenAndEstadoInAndProveedor(startDate, endDate, estadoList,
                    proveedorIdFilter, pageable);
        }

        return ordenCompraRepo.findByFechaEmisionBetweenAndEstadoIn(startDate, endDate, estadoList, pageable);
    }

    @Transactional
    public OrdenCompraMateriales cancelOrdenCompra(int ordenCompraId) {
        OrdenCompraMateriales orden = ordenCompraRepo.findById(ordenCompraId)
                .orElseThrow(() -> new RuntimeException("OrdenCompraMateriales not found with id: " + ordenCompraId));

        // Verificar si la orden ya fue enviada al proveedor (estado 2)
        if (orden.getEstado() == 2) {
            try {
                // Enviar correo de notificación de cancelación al proveedor con copia a usuarios nivel 2
                List<String> ccEmails = getEmailsUsuariosProduccionNivel2();
                if (ccEmails.isEmpty()) {
                    enviarCorreoCancelacionOrdenCompra(orden);
                } else {
                    enviarCorreoCancelacionOrdenCompra_wCC(orden, ccEmails);
                }
                log.info("Correo de cancelación enviado al proveedor para la orden ID: {}", ordenCompraId);
            } catch (Exception e) {
                log.error("Error al enviar correo de cancelación al proveedor: {}", e.getMessage(), e);
                // Continuamos con la cancelación pero registramos el error
            }
        }

        // Cambiar el estado a cancelado
        orden.setEstado(-1);
        return ordenCompraRepo.save(orden);
    }

    /**
     * Envía un correo electrónico al proveedor con la orden de compra adjunta.
     * 
     * @param orden La orden de compra a enviar
     * @param pdfAttachment El archivo PDF adjunto con los detalles de la orden
     * @throws MessagingException Si hay un error al enviar el correo
     * @throws IOException Si hay un error al procesar el archivo adjunto
     * @throws RuntimeException Si no se encuentra un email para el proveedor
     */
    public void enviarCorreoOrdenCompraProveedor(OrdenCompraMateriales orden, MultipartFile pdfAttachment) 
            throws MessagingException, IOException {
        log.info("Iniciando envío de correo para orden ID: {}, Proveedor: {}", 
                 orden.getOrdenCompraId(), orden.getProveedor().getNombre());

        if (pdfAttachment == null || pdfAttachment.isEmpty()) {
            log.error("PDF no proporcionado o vacío para orden ID: {}", orden.getOrdenCompraId());
            throw new RuntimeException("No se proporcionó archivo PDF para enviar por email para la orden: " + orden.getOrdenCompraId());
        }

        log.info("PDF adjunto recibido: nombre={}, tamaño={} bytes, tipo={}", 
                 pdfAttachment.getOriginalFilename(), pdfAttachment.getSize(), pdfAttachment.getContentType());

        // Obtener el proveedor y su información de contacto
        Proveedor proveedor = orden.getProveedor();
        log.info("Datos del proveedor - ID: {}, Nombre: {}, Contactos: {}", 
                 proveedor.getId(), proveedor.getNombre(), proveedor.getContactos());

        // Buscar el email del proveedor en la lista de contactos
        String emailProveedor = null;
        for (ContactoProveedor contacto : proveedor.getContactos()) {
            log.info("Revisando contacto: {}", contacto);
            if (contacto.getEmail() != null && !contacto.getEmail().isBlank()) {
                emailProveedor = contacto.getEmail();
                log.info("Email encontrado para el proveedor: {}", emailProveedor);
                break;
            }
        }

        if (emailProveedor == null) {
            log.error("No se encontró email para el proveedor ID: {}, Nombre: {}", 
                     proveedor.getId(), proveedor.getNombre());
            throw new RuntimeException("No se encontró email para el proveedor con ID: " + proveedor.getId());
        }

        // Preparar el asunto y cuerpo del correo
        String subject = "No Reply - Orden de Compra Exotic Expert #" + orden.getOrdenCompraId();
        String text = "Estimado proveedor: " + orden.getProveedor().getNombre() + ",\n\n" +
                "Por medio de la presente le hacemos llegar la orden de compra #" + orden.getOrdenCompraId() +
                "correspondiente a los productos/servicios detallados en el documento adjunto.\n" +
                "Le agradeceremos confirmar la recepción de esta orden y, en caso de ser necesario," +
                "informarnos sobre el tiempo estimado de entrega o cualquier observación relevante.\n\n" +
                "Quedamos atentos a su confirmación y agradecemos de antemano su atención y colaboración.\n\n" +
                "Saludos cordiales,\n" +
                "Exotic Expert - Departamento de Compras";

        // Enviar el correo con el PDF adjunto
        emailService.sendEmailWithAttachment(
                emailProveedor,
                subject,
                text,
                pdfAttachment
        );

        log.info("Email sent to provider {} with order details for order ID: {}", emailProveedor, orden.getOrdenCompraId());
    }

    /**
     * Envía un correo electrónico al proveedor con copia a otros destinatarios.
     * Funciona igual que {@link #enviarCorreoOrdenCompraProveedor(OrdenCompraMateriales, MultipartFile)}
     * pero permite especificar destinatarios en copia (CC).
     *
     * @param orden       La orden de compra a enviar
     * @param pdfAttachment El archivo PDF adjunto con los detalles de la orden
     * @param ccEmails    Lista de correos electrónicos que recibirán copia
     * @throws MessagingException Si hay un error al enviar el correo
     * @throws IOException        Si hay un error al procesar el archivo adjunto
     * @throws RuntimeException   Si no se encuentra un email para el proveedor
     */
    public void enviarCorreoOrdenCompraProveedor_wCC(OrdenCompraMateriales orden, MultipartFile pdfAttachment, List<String> ccEmails)
            throws MessagingException, IOException {
        log.info("Iniciando envío de correo con CC para orden ID: {}, Proveedor: {}, CC: {}", 
                 orden.getOrdenCompraId(), orden.getProveedor().getNombre(), ccEmails);

        if (pdfAttachment == null || pdfAttachment.isEmpty()) {
            log.error("PDF no proporcionado o vacío para orden ID: {}", orden.getOrdenCompraId());
            throw new RuntimeException("No se proporcionó archivo PDF para enviar por email para la orden: " + orden.getOrdenCompraId());
        }

        log.info("PDF adjunto recibido: nombre={}, tamaño={} bytes, tipo={}", 
                 pdfAttachment.getOriginalFilename(), pdfAttachment.getSize(), pdfAttachment.getContentType());

        // Obtener el proveedor y su información de contacto
        Proveedor proveedor = orden.getProveedor();
        log.info("Datos del proveedor - ID: {}, Nombre: {}, Contactos: {}", 
                 proveedor.getId(), proveedor.getNombre(), proveedor.getContactos());

        // Buscar el email del proveedor en la lista de contactos
        String emailProveedor = null;
        for (ContactoProveedor contacto : proveedor.getContactos()) {
            log.info("Revisando contacto: {}", contacto);
            if (contacto.getEmail() != null && !contacto.getEmail().isBlank()) {
                emailProveedor = contacto.getEmail();
                log.info("Email encontrado para el proveedor: {}", emailProveedor);
                break;
            }
        }

        if (emailProveedor == null) {
            log.error("No se encontró email para el proveedor ID: {}, Nombre: {}", 
                     proveedor.getId(), proveedor.getNombre());
            throw new RuntimeException("No se encontró email para el proveedor con ID: " + proveedor.getId());
        }

        String subject = "No Reply - Orden de Compra Exotic Expert #" + orden.getOrdenCompraId();
        String text = "Estimado proveedor: " + orden.getProveedor().getNombre() + ",\n\n" +
                "Por medio de la presente le hacemos llegar la orden de compra #" + orden.getOrdenCompraId() +
                "correspondiente a los productos/servicios detallados en el documento adjunto.\n" +
                "Le agradeceremos confirmar la recepción de esta orden y, en caso de ser necesario," +
                "informarnos sobre el tiempo estimado de entrega o cualquier observación relevante.\n\n" +
                "Quedamos atentos a su confirmación y agradecemos de antemano su atención y colaboración.\n\n" +
                "Saludos cordiales,\n" +
                "Exotic Expert - Departamento de Compras";

        emailService.sendEmailWithAttachmentAndCC(
                emailProveedor,
                ccEmails.toArray(new String[0]),
                subject,
                text,
                pdfAttachment
        );

        log.info("Email sent to provider {} with CC {} for order ID: {}", emailProveedor, ccEmails, orden.getOrdenCompraId());
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

    @Transactional
    public OrdenCompraMateriales updateEstadoOrdenCompra(int ordenCompraId, UpdateEstadoOrdenCompraRequest ue) {
        log.info("Iniciando actualización de estado para orden de compra ID: {}, nuevo estado: {}", ordenCompraId, ue.getNewEstado());

        OrdenCompraMateriales orden = ordenCompraRepo.findById(ordenCompraId)
                .orElseThrow(() -> new RuntimeException("OrdenCompraMateriales not found with id: " + ordenCompraId));

        log.info("Orden encontrada. Estado actual: {}, Proveedor ID: {}, Nombre: {}", 
                 orden.getEstado(), orden.getProveedor().getId(), orden.getProveedor().getNombre());

        // Si el nuevo estado es 2 y estamos cambiando desde estado 1, manejar según el tipo de envío
        if (ue.getNewEstado() == 2 && orden.getEstado() == 1) {
            log.info("Cambiando estado de 1 a 2 para orden ID: {}", ordenCompraId);

            // Verificar el tipo de envío seleccionado
            if (ue.getTipoEnvio() != null) {
                log.info("Tipo de envío seleccionado: {}", ue.getTipoEnvio());

                switch (ue.getTipoEnvio()) {
                    case MANUAL:
                        // Para envío manual, solo se actualiza el estado sin procesar el PDF
                        log.info("Orden de compra {} actualizada manualmente a estado 2", ordenCompraId);
                        break;

                    case EMAIL:
                        // Para envío por email, verificar que exista el PDF y enviarlo
                        try {
                            List<String> ccEmails = getEmailsUsuariosProduccionNivel2();
                            if (ccEmails.isEmpty()) {
                                enviarCorreoOrdenCompraProveedor(orden, ue.getOCMpdf());
                            } else {
                                enviarCorreoOrdenCompraProveedor_wCC(orden, ue.getOCMpdf(), ccEmails);
                            }
                        } catch (MessagingException | IOException e) {
                            // Lanzar una excepción para que la transacción se revierta
                            log.error("Error al enviar correo al proveedor: {}", e.getMessage(), e);
                            throw new RuntimeException("Error al enviar correo al proveedor: " + e.getMessage(), e);
                        } catch (RuntimeException e) {
                            // Propagar la excepción para que la transacción se revierta
                            log.error(e.getMessage());
                            throw e;
                        }
                        break;

                    case WHATSAPP:
                        // TODO: Implementar envío por WhatsApp en el futuro
                        log.info("Envío por WhatsApp seleccionado para orden {}, esta funcionalidad será implementada próximamente", ordenCompraId);
                        throw new UnsupportedOperationException("El envío por WhatsApp aún no está implementado");

                    default:
                        log.warn("Tipo de envío no reconocido para la orden: {}", ordenCompraId);
                        throw new RuntimeException("Tipo de envío no reconocido para la orden: " + ordenCompraId);
                }
            } else {
                // Si no se especificó tipo de envío, usar el comportamiento predeterminado (email)
                log.warn("No se especificó tipo de envío para la orden: {}, usando email por defecto", ordenCompraId);

                try {
                    List<String> ccEmails = getEmailsUsuariosProduccionNivel2();
                    if (ccEmails.isEmpty()) {
                        enviarCorreoOrdenCompraProveedor(orden, ue.getOCMpdf());
                    } else {
                        enviarCorreoOrdenCompraProveedor_wCC(orden, ue.getOCMpdf(), ccEmails);
                    }
                } catch (MessagingException | IOException e) {
                    // Lanzar una excepción para que la transacción se revierta
                    log.error("Error al enviar correo al proveedor: {}", e.getMessage(), e);
                    throw new RuntimeException("Error al enviar correo al proveedor: " + e.getMessage(), e);
                } catch (RuntimeException e) {
                    // Propagar la excepción para que la transacción se revierta
                    log.error(e.getMessage());
                    throw e;
                }
            }
        }

        // Actualizar el estado solo después de que todo el proceso haya sido exitoso
        orden.setEstado(ue.getNewEstado());
        return ordenCompraRepo.save(orden);
    }





    /**
     * Envía un correo electrónico al proveedor notificando la cancelación de la orden de compra.
     * 
     * @param orden La orden de compra cancelada
     * @throws MessagingException Si hay un error al enviar el correo
     * @throws RuntimeException Si no se encuentra un email para el proveedor
     */
    public void enviarCorreoCancelacionOrdenCompra(OrdenCompraMateriales orden) 
            throws MessagingException {
        log.info("Iniciando envío de correo de cancelación para orden ID: {}, Proveedor: {}", 
                 orden.getOrdenCompraId(), orden.getProveedor().getNombre());

        // Obtener el proveedor y su información de contacto
        Proveedor proveedor = orden.getProveedor();
        log.info("Datos del proveedor - ID: {}, Nombre: {}", 
                 proveedor.getId(), proveedor.getNombre());

        // Buscar el email del proveedor en la lista de contactos
        String emailProveedor = null;
        for (ContactoProveedor contacto : proveedor.getContactos()) {
            if (contacto.getEmail() != null && !contacto.getEmail().isBlank()) {
                emailProveedor = contacto.getEmail();
                log.info("Email encontrado para el proveedor: {}", emailProveedor);
                break;
            }
        }

        if (emailProveedor == null) {
            log.error("No se encontró email para el proveedor ID: {}, Nombre: {}", 
                     proveedor.getId(), proveedor.getNombre());
            throw new RuntimeException("No se encontró email para el proveedor con ID: " + proveedor.getId());
        }

        // Preparar el asunto y cuerpo del correo
        String subject = "Cancelación - Orden de Compra Exotic Expert #" + orden.getOrdenCompraId();
        String text = "Estimado proveedor: " + orden.getProveedor().getNombre() + ",\n\n" +
                "Por medio de la presente le informamos que la orden de compra #" + orden.getOrdenCompraId() +
                " ha sido cancelada.\n\n" +
                "Lamentamos cualquier inconveniente que esto pueda causar. Si tiene alguna consulta o requiere " +
                "información adicional, no dude en contactarnos.\n\n" +
                "Saludos cordiales,\n" +
                "Exotic Expert - Departamento de Compras";

        // Enviar el correo
        emailService.sendSimpleEmail(
                emailProveedor,
                subject,
                text
        );

        log.info("Email de cancelación enviado al proveedor {} para orden ID: {}", emailProveedor, orden.getOrdenCompraId());
    }

    /**
     * Envía un correo electrónico al proveedor notificando la cancelación de la orden de compra,
     * con copia a los usuarios especificados.
     * 
     * @param orden La orden de compra cancelada
     * @param ccEmails Lista de correos electrónicos que recibirán copia
     * @throws MessagingException Si hay un error al enviar el correo
     * @throws IOException Si hay un error al procesar el correo
     * @throws RuntimeException Si no se encuentra un email para el proveedor
     */
    public void enviarCorreoCancelacionOrdenCompra_wCC(OrdenCompraMateriales orden, List<String> ccEmails) 
            throws MessagingException, IOException {
        log.info("Iniciando envío de correo de cancelación con CC para orden ID: {}, Proveedor: {}, CC: {}", 
                 orden.getOrdenCompraId(), orden.getProveedor().getNombre(), ccEmails);

        // Obtener el proveedor y su información de contacto
        Proveedor proveedor = orden.getProveedor();
        log.info("Datos del proveedor - ID: {}, Nombre: {}", 
                 proveedor.getId(), proveedor.getNombre());

        // Buscar el email del proveedor en la lista de contactos
        String emailProveedor = null;
        for (ContactoProveedor contacto : proveedor.getContactos()) {
            if (contacto.getEmail() != null && !contacto.getEmail().isBlank()) {
                emailProveedor = contacto.getEmail();
                log.info("Email encontrado para el proveedor: {}", emailProveedor);
                break;
            }
        }

        if (emailProveedor == null) {
            log.error("No se encontró email para el proveedor ID: {}, Nombre: {}", 
                     proveedor.getId(), proveedor.getNombre());
            throw new RuntimeException("No se encontró email para el proveedor con ID: " + proveedor.getId());
        }

        // Preparar el asunto y cuerpo del correo
        String subject = "Cancelación - Orden de Compra Exotic Expert #" + orden.getOrdenCompraId();
        String text = "Estimado proveedor: " + orden.getProveedor().getNombre() + ",\n\n" +
                "Por medio de la presente le informamos que la orden de compra #" + orden.getOrdenCompraId() +
                " ha sido cancelada.\n\n" +
                "Lamentamos cualquier inconveniente que esto pueda causar. Si tiene alguna consulta o requiere " +
                "información adicional, no dude en contactarnos.\n\n" +
                "Saludos cordiales,\n" +
                "Exotic Expert - Departamento de Compras";

        // Enviar el correo con copia (sin adjunto)
        emailService.sendEmailWithAttachmentAndCC(
                emailProveedor,
                ccEmails.toArray(new String[0]),
                subject,
                text,
                null
        );

        log.info("Email de cancelación enviado al proveedor {} con CC {} para orden ID: {}", 
                emailProveedor, ccEmails, orden.getOrdenCompraId());
    }

    public OrdenCompraMateriales getOrdenCompraByOrdenCompraIdAndEstado(Integer ordenCompraId, int estado) {
        return ordenCompraRepo.findByOrdenCompraIdAndEstado(ordenCompraId, estado)
                .orElseThrow(() -> new RuntimeException("OrdenCompraMateriales not found with OrdenCompraId: "
                        + ordenCompraId + " and estado = " + estado));
    }

    /**
     * Actualiza una orden de compra existente.
     * 
     * @param ordenCompraId ID de la orden de compra a actualizar
     * @param ordenCompraMateriales Objeto con los datos actualizados
     * @return La orden de compra actualizada
     * @throws RuntimeException Si la orden no existe o hay algún error en la actualización
     */
    @Transactional
    public OrdenCompraMateriales updateOrdenCompra(int ordenCompraId, OrdenCompraMateriales ordenCompraMateriales) {
        logProveedorPayloadAnomaly("updateOrdenCompra", ordenCompraMateriales);

        if (ordenCompraMateriales == null || ordenCompraMateriales.getProveedor() == null || isBlank(ordenCompraMateriales.getProveedor().getId())) {
            throw new IllegalArgumentException("La orden de compra debe incluir un proveedor valido.");
        }

        // Verificar que la orden de compra existe
        OrdenCompraMateriales ordenExistente = ordenCompraRepo.findById(ordenCompraId)
                .orElseThrow(() -> new RuntimeException("Orden de compra no encontrada con ID: " + ordenCompraId));

        // Verificar que el proveedor existe
        Proveedor proveedor = proveedorRepo.findById(ordenCompraMateriales.getProveedor().getId().trim())
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado con ID: " + 
                        ordenCompraMateriales.getProveedor().getId()));

        // Actualizar los campos básicos de la orden
        ordenExistente.setFechaVencimiento(ordenCompraMateriales.getFechaVencimiento());
        ordenExistente.setProveedor(proveedor);
        ordenExistente.setCondicionPago(ordenCompraMateriales.getCondicionPago());
        ordenExistente.setTiempoEntrega(ordenCompraMateriales.getTiempoEntrega());
        ordenExistente.setPlazoPago(ordenCompraMateriales.getPlazoPago());
        ordenExistente.setSubTotal(ordenCompraMateriales.getSubTotal());
        ordenExistente.setIvaCOP(ordenCompraMateriales.getIvaCOP());
        ordenExistente.setTotalPagar(ordenCompraMateriales.getTotalPagar());

        // Actualizar los nuevos atributos de divisa y TRM
        ordenExistente.setDivisas(ordenCompraMateriales.getDivisas());
        ordenExistente.setTrm(ordenCompraMateriales.getTrm());
        ordenExistente.setObservaciones(ordenCompraMateriales.getObservaciones());

        // No modificamos el estado ni la fecha de emisión aquí
        // Si se necesita cambiar el estado, se debe usar updateEstadoOrdenCompra

        // Eliminar todos los items existentes y agregar los nuevos
        ordenExistente.getItemsOrdenCompra().clear();

        // Agregar los nuevos items
        for (ItemOrdenCompra item : ordenCompraMateriales.getItemsOrdenCompra()) {
            // Verificar que el material existe
            Material material = materialRepo.findById(item.getMaterial().getProductoId())
                    .orElseThrow(() -> new RuntimeException("Material no encontrado con ID: " + 
                            item.getMaterial().getProductoId()));

            // Crear un nuevo item y configurarlo
            ItemOrdenCompra nuevoItem = new ItemOrdenCompra();
            nuevoItem.setOrdenCompraMateriales(ordenExistente);
            nuevoItem.setMaterial(material);
            nuevoItem.setCantidad(item.getCantidad());
            nuevoItem.setPrecioUnitario(item.getPrecioUnitario());
            nuevoItem.setIvaCOP(item.getIvaCOP());
            nuevoItem.setSubTotal(item.getSubTotal());
            nuevoItem.setCantidadCorrecta(item.getCantidadCorrecta());
            nuevoItem.setPrecioCorrecto(item.getPrecioCorrecto());

            // Agregar el item a la orden
            ordenExistente.getItemsOrdenCompra().add(nuevoItem);
        }

        // Guardar y retornar la orden actualizada
        return ordenCompraRepo.save(ordenExistente);
    }

    private void logProveedorPayloadAnomaly(String operation, OrdenCompraMateriales ordenCompraMateriales) {
        if (ordenCompraMateriales == null) {
            log.warn("Payload OCM invalido en {}. ordenCompraMateriales=null", operation);
            return;
        }

        Proveedor proveedor = ordenCompraMateriales.getProveedor();
        List<String> issues = new ArrayList<>();

        if (proveedor == null) {
            issues.add("proveedor_null");
        } else {
            if (isBlank(proveedor.getId())) {
                issues.add("proveedor_business_id_blank");
            }
            if (isBlank(proveedor.getNombre())) {
                issues.add("proveedor_nombre_blank");
            }
        }

        if (issues.isEmpty()) {
            return;
        }

        log.warn(
                "Payload OCM con proveedor inconsistente en {}. motivos={}, ordenCompraId={}, estado={}, fechaVencimiento={}, facturaCompraId={}, itemsCount={}, proveedorPk={}, proveedorId={}, proveedorNombre={}",
                operation,
                issues,
                ordenCompraMateriales.getOrdenCompraId(),
                ordenCompraMateriales.getEstado(),
                ordenCompraMateriales.getFechaVencimiento(),
                ordenCompraMateriales.getFacturaCompraId(),
                ordenCompraMateriales.getItemsOrdenCompra() != null ? ordenCompraMateriales.getItemsOrdenCompra().size() : 0,
                proveedor != null ? proveedor.getPk() : null,
                proveedor != null ? proveedor.getId() : null,
                proveedor != null ? proveedor.getNombre() : null
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }


    /**
     * Cierra una orden de compra (establece estado 3).
     * Solo se puede cerrar si:
     * - La orden está en estado 2 (pendiente ingreso almacén)
     * - Tiene al menos una transacción de almacén de ingreso asociada
     *
     * @param ordenCompraId ID de la orden de compra a cerrar
     * @return La orden de compra cerrada
     * @throws RuntimeException Si la orden no existe, no está en estado 2, o no tiene transacciones
     */
    @Transactional
    public OrdenCompraMateriales closeOrdenCompra(int ordenCompraId) {
        log.info("Iniciando cierre de orden de compra ID: {}", ordenCompraId);

        OrdenCompraMateriales orden = ordenCompraRepo.findById(ordenCompraId)
                .orElseThrow(() -> new RuntimeException("OrdenCompraMateriales not found with id: " + ordenCompraId));

        // Verificar que la orden esté en estado 2 (pendiente ingreso almacén)
        if (orden.getEstado() != 2) {
            throw new RuntimeException("No se puede cerrar la orden de compra ID: " + ordenCompraId +
                    " porque no está en estado 2 (pendiente ingreso almacén). Estado actual: " + orden.getEstado());
        }

        // Verificar que existe al menos una transacción de almacén asociada
        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausante(
                        TransaccionAlmacen.TipoEntidadCausante.OCM,
                        ordenCompraId
                );

        if (transacciones.isEmpty()) {
            throw new RuntimeException("No se puede cerrar la orden de compra ID: " + ordenCompraId +
                    " porque no tiene ninguna transacción de almacén de ingreso registrada.");
        }

        log.info("Orden ID: {} tiene {} transacción(es) de almacén. Procediendo a cerrar la orden.",
                ordenCompraId, transacciones.size());

        // Cambiar el estado a cerrado exitosamente (3)
        orden.setEstado(3);
        OrdenCompraMateriales ordenCerrada = ordenCompraRepo.save(orden);

        log.info("Orden de compra ID: {} cerrada exitosamente.", ordenCompraId);

        return ordenCerrada;
    }

}
