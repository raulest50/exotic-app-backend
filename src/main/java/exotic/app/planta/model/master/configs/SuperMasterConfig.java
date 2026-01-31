package exotic.app.planta.model.master.configs;

import jakarta.persistence.*;
import lombok.*;

/**
 * Singleton configuration for Super Master directives.
 * Controls visibility of: Eliminación Forzada, Carga Masiva, Ajustes Inventario.
 */
@Entity
@Table(name = "super_master_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperMasterConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private boolean habilitarEliminacionForzada;
    private boolean habilitarCargaMasiva;
    private boolean habilitarAjustesInventario;
}
