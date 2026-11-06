package exotic.app.planta.model.produccion.despeje;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Edición de negocio por área. El contenido publicado no se reemplaza. */
@Entity
@Table(name = "despeje_plantilla_version", uniqueConstraints =
        @UniqueConstraint(name = "uq_despeje_plantilla_numero", columnNames = {"area_operativa_id", "numero"}))
@Getter @Setter @NoArgsConstructor
public class PlantillaDespejeVersion {
    public enum Estado { BORRADOR, VIGENTE, RETIRADA }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_operativa_id", nullable = false, updatable = false)
    private AreaOperativa areaOperativa;
    @Column(nullable = false, updatable = false)
    private int numero;
    @Column(nullable = false, length = 200)
    private String nombre;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Estado estado = Estado.BORRADOR;
    @Column(name = "motivo_cambio", nullable = false, columnDefinition = "TEXT")
    private String motivoCambio;
    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creada_por_id", nullable = false, updatable = false)
    private User creadaPor;
    @Column(name = "vigente_desde")
    private LocalDateTime vigenteDesde;
    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;
    @OneToMany(mappedBy = "plantilla", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<ItemPlantillaDespeje> items = new ArrayList<>();
    @Version @Column(nullable = false)
    private long version;

    @Transient private String nombreCargado;
    @Transient private String motivoCargado;
    @Transient private boolean publicada;
    @PostLoad @PostPersist
    private void recordarContenido() {
        nombreCargado = nombre;
        motivoCargado = motivoCambio;
        publicada = estado != Estado.BORRADOR;
    }
    @PreUpdate
    private void preservarPublicacion() {
        if (publicada && (estado == Estado.BORRADOR
                || !java.util.Objects.equals(nombreCargado, nombre)
                || !java.util.Objects.equals(motivoCargado, motivoCambio))) {
            throw new IllegalStateException("El contenido de una plantilla publicada es inmutable.");
        }
    }
    @PreRemove
    private void preservarHistoria() {
        if (estado != Estado.BORRADOR) throw new IllegalStateException("No se elimina una plantilla publicada.");
    }
}
