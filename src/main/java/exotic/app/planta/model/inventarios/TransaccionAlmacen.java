    package exotic.app.planta.model.inventarios;

    import com.fasterxml.jackson.annotation.JsonBackReference;
    import com.fasterxml.jackson.annotation.JsonManagedReference;
    import jakarta.persistence.*;
    import exotic.app.planta.model.contabilidad.AsientoContable;
    import exotic.app.planta.model.inventarios.dto.IngresoOCM_DTA;
    import exotic.app.planta.model.users.User;
    import lombok.AllArgsConstructor;
    import lombok.Getter;
    import lombok.NoArgsConstructor;
    import lombok.Setter;
    import org.hibernate.annotations.CreationTimestamp;

    import java.time.LocalDateTime;
    import java.util.List;


    /**
     * Representa una transacción de almacén que agrupa múltiples movimientos de inventario
     * como una operación atómica.
     *
     * <p>Una transacción de almacén es el <strong>agregado raíz</strong> (DDD pattern) que encapsula
     * uno o más {@link Movimiento}s de productos. Garantiza que todos los movimientos se ejecuten
     * de forma atómica (ACID) y mantiene la trazabilidad hacia la entidad de negocio que causó
     * la transacción.
     *
     * <h2>Decisión de Arquitectura: Múltiples Almacenes</h2>
     * <p>Una {@code TransaccionAlmacen} <strong>PUEDE contener movimientos a diferentes almacenes</strong>
     * en la misma transacción. Esto permite operaciones como:
     *
     * <ul>
     *   <li><strong>Transferencias (OTA):</strong> Mover productos de {@code GENERAL} → {@code AVERIAS}
     *       en una sola transacción atómica</li>
     *   <li><strong>Producción con scrap (OP):</strong> Consumos en {@code GENERAL}, backflush a
     *       {@code GENERAL}, y scrap directo a {@code AVERIAS}</li>
     *   <li><strong>Ajustes con reclasificación (OAA):</strong> Ajustar cantidades moviendo productos
     *       entre almacenes</li>
     * </ul>
     *
     * <p>Esta decisión sigue los estándares de SAP (transacción MB1B) y Oracle WMS, donde una
     * transferencia entre ubicaciones se registra como una única operación con múltiples líneas.
     *
     * <h3>Validaciones por Tipo</h3>
     * <p>Aunque el modelo permite múltiples almacenes, cada {@link TipoEntidadCausante} tiene reglas
     * específicas que deben validarse en el Service Layer:
     *
     * <table border="1">
     *   <tr>
     *     <th>Tipo</th>
     *     <th>Almacenes Permitidos</th>
     *     <th>Razón</th>
     *   </tr>
     *   <tr>
     *     <td>OCM</td>
     *     <td>Solo GENERAL</td>
     *     <td>Las compras se reciben en un punto único</td>
     *   </tr>
     *   <tr>
     *     <td>OD</td>
     *     <td>Solo GENERAL</td>
     *     <td>La dispensación se hace desde un almacén específico</td>
     *   </tr>
     *   <tr>
     *     <td>OTA</td>
     *     <td>Exactamente 2 diferentes</td>
     *     <td>Transferencia origen → destino</td>
     *   </tr>
     *   <tr>
     *     <td>OP</td>
     *     <td>1 o más</td>
     *     <td>Consumos y backflush en GENERAL, scrap opcional a AVERIAS</td>
     *   </tr>
     *   <tr>
     *     <td>OAA</td>
     *     <td>1 o más</td>
     *     <td>Puede ser ajuste simple o reclasificación entre almacenes</td>
     *   </tr>
     * </table>
     *
     * <h3>Consistencia Contable</h3>
     * <p>Cada transacción puede tener asociado un único {@link AsientoContable}, independientemente
     * de cuántos almacenes o productos afecte. Esto garantiza la trazabilidad contable completa.
     *
     * <p><strong>📚 Documentación completa:</strong>
     * <a href="../../../../../docs/adr/001-transaccion-almacen-multiples-ubicaciones.md">
     * ADR 001: TransaccionAlmacen con Múltiples Almacenes
     * </a>
     *
     * @see Movimiento
     * @see TipoEntidadCausante
     * @see Movimiento.Almacen
     * @see AsientoContable
     * @since 1.0
     */
    @Entity
    @Table(name = "transaccion_almacen")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public class TransaccionAlmacen {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(unique = true, updatable = false, nullable = false)
        private int transaccionId;

        @OneToMany(mappedBy = "transaccionAlmacen", cascade = CascadeType.ALL, orphanRemoval = true)
        @JsonManagedReference
        private List<Movimiento> movimientosTransaccion;

        @CreationTimestamp
        private LocalDateTime fechaTransaccion;

        /**
         * url de la foto, scan o documento fisico de soporte si lo hay
         */
        private String urlDocSoporte;

        /**
         * Estado contable de la transacción
         */
        @Enumerated(EnumType.STRING)
        private EstadoContable estadoContable = EstadoContable.PENDIENTE;

        /**
         * Referencia al asiento contable asociado a esta transacción
         */
        @OneToOne
        @JoinColumn(name = "asiento_contable_id")
        private AsientoContable asientoContable;

        private TipoEntidadCausante tipoEntidadCausante;

        /**
         * Una transaccion de almancen se compone de 1 o mas movimientos de inventario.
         * no pouede haber ningun movimiento de almancen que no este asociado a una
         * transaccion de inventario. De la misma forma ninguna transaccion de
         * inventario o almancen puede existir si no esta asociada a una entidad
         * causante, por Ej: Orden de Compra de materiales (OCM), Orden de Produccion,
         * Orden de ajuste de almance (OAA).
         */
        private int idEntidadCausante;

        private String observaciones;

        /**
         * Lista de usuarios responsables de realizar la dispensación.
         * Puede ser null si no hay usuarios asignados.
         * Se usa principalmente para dispensaciones donde múltiples usuarios participan.
         */
        @ManyToMany
        @JoinTable(
                name = "transaccion_almacen_usuarios_realizadores",
                joinColumns = @JoinColumn(name = "transaccion_id"),
                inverseJoinColumns = @JoinColumn(name = "usuario_id")
        )
        private List<User> usuariosResponsables;

        /**
         * Usuario que aprueba la dispensación.
         * Puede ser null si no hay aprobador asignado.
         * Generalmente es el supervisor o responsable del almacén.
         */
        @ManyToOne
        @JoinColumn(name = "usuario_aprobador_id")
        @JsonBackReference(value = "aprobador-transacciones")
        private User usuarioAprobador;

        public TransaccionAlmacen(IngresoOCM_DTA ingresoOCM_dta) {
            this.movimientosTransaccion = ingresoOCM_dta.getTransaccionAlmacen().getMovimientosTransaccion();
            this.tipoEntidadCausante = TipoEntidadCausante.OCM;
            this.idEntidadCausante = ingresoOCM_dta.getOrdenCompraMateriales().getOrdenCompraId();
            this.observaciones = ingresoOCM_dta.getObservaciones();
            this.estadoContable = EstadoContable.PENDIENTE; // Por defecto, pendiente de contabilización
            // El usuario se asignará en el servicio
        }


        /**
         * Tipos de entidades de negocio que pueden causar una transacción de almacén.
         *
         * <p>Cada tipo determina el origen de la transacción y tiene reglas específicas sobre
         * los almacenes que puede afectar. Las validaciones se implementan en el Service Layer.
         *
         * <h3>Reglas por Tipo</h3>
         * <ul>
         *   <li><strong>{@link #OCM}</strong>: Solo puede afectar almacén {@code GENERAL} (ingreso de compras)</li>
         *   <li><strong>{@link #OD}</strong>: Solo puede afectar almacén {@code GENERAL} (dispensación)</li>
         *   <li><strong>{@link #OTA}</strong>: Debe afectar exactamente 2 almacenes diferentes (transferencia)</li>
         *   <li><strong>{@link #OP}</strong>: Puede afectar 1 o más almacenes (producción con scrap opcional)</li>
         *   <li><strong>{@link #OAA}</strong>: Puede afectar 1 o más almacenes (ajustes/reclasificaciones)</li>
         *   <li><strong>{@link #CM}</strong>: Típicamente afecta solo {@code GENERAL} (carga inicial)</li>
         * </ul>
         *
         * <p><strong>📚 Ver:</strong>
         * <a href="../../../../../docs/adr/001-transaccion-almacen-multiples-ubicaciones.md#validaciones-por-tipo">
         * ADR 001 - Validaciones por Tipo
         * </a>
         *
         * @see TransaccionAlmacen#idEntidadCausante
         * @see Movimiento.Almacen
         */
        public enum TipoEntidadCausante{
            OCM, //Orden de Compra de Materiales - siempre ingresa a almacén GENERAL
            OP, //Orden de Producción - puede generar scrap en AVERIAS además del producto en GENERAL
            OTA, //Orden de Transferencia de Almacén - mueve productos entre exactamente 2 almacenes
            OAA, // Orden de Ajuste de Almacén - puede reclasificar productos entre almacenes */
            OD, // Orden de Dispensación - siempre dispensa desde almacén GENERAL
            CM, // Carga Masiva de inventario - típicamente a GENERAL (carga inicial del sistema)
            RA  // Reporte de Avería - para material ya dispensado que se reporta como averiado
        }

        /**
         * Estados posibles para la contabilización de una transacción de almacén.
         *
         * <p>Controla el ciclo de vida contable de la transacción, desde su creación hasta
         * su registro en el libro diario mediante un {@link AsientoContable}.
         *
         * @see AsientoContable
         * @see TransaccionAlmacen#asientoContable
         */
        public enum EstadoContable {
            /** No ha sido contabilizada aún - pendiente de registro contable */
            PENDIENTE,

            /** Ya tiene asiento contable asociado - registro completado */
            CONTABILIZADA,

            /** No requiere contabilización - transacción que no afecta contabilidad */
            NO_APLICA
        }

    }
