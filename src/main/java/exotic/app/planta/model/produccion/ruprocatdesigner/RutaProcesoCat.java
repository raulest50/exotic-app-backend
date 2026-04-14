package exotic.app.planta.model.produccion.ruprocatdesigner;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.producto.Categoria;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ruta_proceso_cat")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RutaProcesoCat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "categoria_id", unique = true)
    private Categoria categoria;

    @OneToMany(mappedBy = "rutaProcesoCat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RutaProcesoNode> nodes = new ArrayList<>();

    @OneToMany(mappedBy = "rutaProcesoCat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RutaProcesoEdge> edges = new ArrayList<>();

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    @PrePersist
    protected void onCreate() {
        fechaCreacion = AppTime.now();
        fechaModificacion = AppTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        fechaModificacion = AppTime.now();
    }
}
