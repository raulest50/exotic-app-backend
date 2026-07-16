package exotic.app.planta.model.produccion;

import exotic.app.planta.model.users.User;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cierre_produccion")
@Getter
@Setter
@NoArgsConstructor
public class CierreProduccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_produccion", nullable = false)
    private LocalDate fechaProduccion;

    @Column(name = "cerrado_en", nullable = false)
    private LocalDateTime cerradoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cerrado_por_id", nullable = false)
    private User cerradoPor;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "solicitud_hash", nullable = false, length = 64)
    private String solicitudHash;

    @OneToMany(mappedBy = "cierreProduccion")
    @OrderBy("id ASC")
    private List<ReporteProduccionLote> reportes = new ArrayList<>();
}
