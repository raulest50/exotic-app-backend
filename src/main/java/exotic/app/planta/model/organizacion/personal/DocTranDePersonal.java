package exotic.app.planta.model.organizacion.personal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Para dar ingreso, hacer una modificacion de salario o algun otro parametro
 * de un integrante del personal, o para dar salida del mismo, se debe generar
 * un documento de esta clase. Esta permite llevar un historial de todas las
 * modificaciones, fecha de ingreso etc de un integrante de personal o colaborador
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "doc_tran_de_personal")
public class DocTranDePersonal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Referencia al integrante de personal al que pertenece este documento
     */
    /*@ManyToOne
    @JoinColumn(name = "idIntegrante", nullable = false)
    private IntegrantePersonal idIntegrante;*/

    /**
     * Tipo de cambio realizado
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDocTran tipoDocTran;

    /**
     * Fecha y hora en que se realizó el cambio
     */
    @Column(nullable = false)
    private LocalDateTime fechaHora;

    /**
     * Descripción del cambio realizado
     */
    @Column(length = 1000)
    private String descripcion;

    /**
     * Valores anteriores en formato JSON (para cambios)
     */
    @Column
    private String valoresAnteriores;

    /**
     * Valores nuevos en formato JSON (para cambios)
     */
    @Column
    private String valoresNuevos;

    /**
     * Usuario que realizó el cambio
     */
    private String usuarioResponsable;

    /**
     * Tipos de documentos de personal
     */
    public enum TipoDocTran {
        INGRESO("Ingreso de personal"),
        MODIFICACION_SALARIO("Modificación de salario"),
        MODIFICACION_CARGO("Modificación de cargo"),
        MODIFICACION_DEPARTAMENTO("Modificación de departamento"),
        MODIFICACION_DATOS_PERSONALES("Modificación de datos personales"),
        CAMBIO_ESTADO("Cambio de estado"),
        SALIDA("Salida de personal"),
        OTRO("Otro tipo de cambio");

        private final String descripcion;

        TipoDocTran(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    /**
     * Crea un documento de ingreso para un nuevo integrante de personal
     */
    public static DocTranDePersonal crearDocumentoIngreso(IntegrantePersonal integrante, String usuarioResponsable) {
        DocTranDePersonal documento = new DocTranDePersonal();
        //documento.setIdIntegrante(integrante);
        documento.setTipoDocTran(TipoDocTran.INGRESO);
        documento.setFechaHora(LocalDateTime.now());
        documento.setDescripcion("Ingreso de nuevo integrante de personal");
        documento.setUsuarioResponsable(usuarioResponsable);
        return documento;
    }

    /**
     * Crea un documento de modificación para un cambio en los atributos del integrante
     */
    public static DocTranDePersonal crearDocumentoModificacion(
            IntegrantePersonal integrante, 
            TipoDocTran tipoDocTran,
            String descripcion,
            String valoresAnteriores,
            String valoresNuevos,
            String usuarioResponsable) {

        DocTranDePersonal documento = new DocTranDePersonal();
        //documento.setIdIntegrante(integrante);
        documento.setTipoDocTran(tipoDocTran);
        documento.setFechaHora(LocalDateTime.now());
        documento.setDescripcion(descripcion);
        documento.setValoresAnteriores(valoresAnteriores);
        documento.setValoresNuevos(valoresNuevos);
        documento.setUsuarioResponsable(usuarioResponsable);
        return documento;
    }

    /**
     * Crea un documento de salida para un integrante que deja la empresa
     */
    public static DocTranDePersonal crearDocumentoSalida(
            IntegrantePersonal integrante,
            String motivo,
            String usuarioResponsable) {

        DocTranDePersonal documento = new DocTranDePersonal();
        //documento.setIdIntegrante(integrante);
        documento.setTipoDocTran(TipoDocTran.SALIDA);
        documento.setFechaHora(LocalDateTime.now());
        documento.setDescripcion("Salida de integrante: " + motivo);
        documento.setUsuarioResponsable(usuarioResponsable);
        return documento;
    }
}
