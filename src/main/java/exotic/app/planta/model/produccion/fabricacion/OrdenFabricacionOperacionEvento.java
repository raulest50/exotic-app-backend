package exotic.app.planta.model.produccion.fabricacion;

import exotic.app.planta.model.produccion.ActorTipoEventoSeguimiento;
import exotic.app.planta.model.produccion.TipoEventoSeguimiento;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Bitacora inmutable de transiciones de una operacion de OF. */
@Entity
@Table(name = "orden_fabricacion_operacion_evento")
@Getter
@Setter
@NoArgsConstructor
public class OrdenFabricacionOperacionEvento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operacion_id", nullable = false, updatable = false)
    private OrdenFabricacionOperacion operacion;

    @Column(name = "estado_origen", updatable = false)
    private Integer estadoOrigen;

    @Column(name = "estado_destino", nullable = false, updatable = false)
    private int estadoDestino;

    @Column(name = "fecha_evento", nullable = false, updatable = false)
    private LocalDateTime fechaEvento;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_tipo", nullable = false, length = 16, updatable = false)
    private ActorTipoEventoSeguimiento actorTipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 32, updatable = false)
    private TipoEventoSeguimiento tipoEvento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_revertido_id", updatable = false)
    private OrdenFabricacionOperacionEvento eventoRevertido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", updatable = false)
    private User usuario;

    @Column(length = 500, updatable = false)
    private String nota;

    @PreUpdate
    private void impedirModificacion() {
        throw new IllegalStateException("Un evento operativo de OF es inmutable.");
    }

    @PreRemove
    private void impedirEliminacion() {
        throw new IllegalStateException("Un evento operativo de OF no puede eliminarse.");
    }
}
