package exotic.app.planta.model.empresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "empresa_identidad_legal_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmpresaIdentidadLegalVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "razon_social", nullable = false, length = 255)
    private String razonSocial;

    @Column(name = "nombre_comercial", nullable = false, length = 160)
    private String nombreComercial;

    @Column(name = "tipo_identificacion", nullable = false, length = 30)
    private String tipoIdentificacion;

    @Column(name = "numero_identificacion", nullable = false, length = 40)
    private String numeroIdentificacion;

    @Column(name = "digito_verificacion", nullable = false, length = 10)
    private String digitoVerificacion;

    @Column(name = "telefono_principal", nullable = false, length = 80)
    private String telefonoPrincipal;

    @Column(name = "email_principal", nullable = false, length = 255)
    private String emailPrincipal;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDateTime vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "creado_por", length = 120)
    private String creadoPor;

    @Column(name = "motivo_cambio", columnDefinition = "TEXT")
    private String motivoCambio;

    public enum Estado {
        VIGENTE,
        RETIRADA
    }
}
