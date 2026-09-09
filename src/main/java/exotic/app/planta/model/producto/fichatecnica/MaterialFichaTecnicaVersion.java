package exotic.app.planta.model.producto.fichatecnica;

import exotic.app.planta.model.producto.Material;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "material_ficha_tecnica_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MaterialFichaTecnicaVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "nombre_archivo_original", nullable = false, length = 255)
    private String nombreArchivoOriginal;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDateTime vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "creado_por", length = 120)
    private String creadoPor;

    @Column(name = "motivo_cambio", nullable = false, columnDefinition = "TEXT")
    private String motivoCambio;

    public enum Estado {
        VIGENTE,
        RETIRADA
    }
}
