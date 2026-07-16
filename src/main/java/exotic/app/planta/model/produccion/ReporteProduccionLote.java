package exotic.app.planta.model.produccion;

import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reporte_produccion_lote")
@Getter
@Setter
@NoArgsConstructor
public class ReporteProduccionLote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_produccion_id", nullable = false)
    private OrdenProduccion ordenProduccion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seguimiento_orden_area_id", nullable = false)
    private SeguimientoOrdenArea seguimientoOrdenArea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cierre_produccion_id")
    private CierreProduccion cierreProduccion;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaccion_almacen_id", unique = true)
    private TransaccionAlmacen transaccionAlmacen;

    @Column(name = "cantidad_reportada", nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidadReportada;

    @Column(name = "cantidad_confirmada", precision = 18, scale = 4)
    private BigDecimal cantidadConfirmada;

    @Column(name = "fecha_produccion", nullable = false)
    private LocalDate fechaProduccion;

    @Column(name = "reportado_en", nullable = false)
    private LocalDateTime reportadoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reportado_por_id", nullable = false)
    private User reportadoPor;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private Estado estado;

    @Column(name = "motivo_correccion", length = 500)
    private String motivoCorreccion;

    @Column(name = "estado_orden_anterior", nullable = false)
    private int estadoOrdenAnterior;

    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulado_por_id")
    private User anuladoPor;

    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public enum Estado {
        PENDIENTE,
        CONFIRMADO,
        ANULADO
    }
}
