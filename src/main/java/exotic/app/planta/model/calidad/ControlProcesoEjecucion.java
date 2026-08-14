package exotic.app.planta.model.calidad;

import com.fasterxml.jackson.annotation.JsonIgnore;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.users.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "calidad_control_proceso_ejecucion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ControlProcesoEjecucion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantilla_id", nullable = false)
    private ControlProcesoPlantilla plantilla;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    /** Expediente del lote al que pertenece este control, si ya fue creado. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_record_id")
    private BatchRecord batchRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @OneToMany(mappedBy = "ejecucion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroMuestra ASC")
    private List<ControlProcesoMuestra> muestras = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void validarBatchRecord() {
        if (batchRecord != null && !mismoLote(lote, batchRecord.getLoteResultado())) {
            throw new IllegalStateException(
                    "El control de proceso debe pertenecer al lote de resultado del batch record.");
        }
    }

    private boolean mismoLote(Lote primero, Lote segundo) {
        return primero == segundo
                || (primero != null
                && segundo != null
                && primero.getId() != null
                && primero.getId().equals(segundo.getId()));
    }
}
