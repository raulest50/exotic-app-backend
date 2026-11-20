package exotic.app.planta.model.produccion;

import exotic.app.planta.model.produccion.dto.ReporteProduccionEtapaDTOs.Reporte;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "seguimiento_orden_area_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeguimientoOrdenAreaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seguimiento_orden_area_id", nullable = false)
    private SeguimientoOrdenArea seguimientoOrdenArea;

    @Column(name = "estado_origen")
    private Integer estadoOrigen;

    @Column(name = "estado_destino", nullable = false)
    private int estadoDestino;

    @Column(name = "fecha_evento", nullable = false)
    private LocalDateTime fechaEvento;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_tipo", nullable = false, length = 16)
    private ActorTipoEventoSeguimiento actorTipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 32)
    private TipoEventoSeguimiento tipoEvento = TipoEventoSeguimiento.OPERATIVO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_revertido_id")
    private SeguimientoOrdenAreaEvento eventoRevertido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @Column(length = 500)
    private String nota;

    /** Evidencia del completado; no modifica inventario ni se sobrescribe al reabrir. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reportes_produccion", columnDefinition = "jsonb", updatable = false)
    private List<Reporte> reportesProduccion;
}
