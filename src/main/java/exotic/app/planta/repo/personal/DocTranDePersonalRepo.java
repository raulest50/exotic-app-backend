package exotic.app.planta.repo.personal;

import exotic.app.planta.model.organizacion.personal.DocTranDePersonal;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for DocTranDePersonal entity
 * This interface provides CRUD operations and custom queries for DocTranDePersonal documents
 */
public interface DocTranDePersonalRepo extends JpaRepository<DocTranDePersonal, Long> {

    /**
     * Find documents by integrante ID
     * 
     * @param integranteId The ID of the IntegrantePersonal
     * @return List of documents associated with the specified integrante
     */
    //List<DocTranDePersonal> findByIdIntegrante_Id(Long integranteId);

    /**
     * Find documents by document type
     * 
     * @param tipoDocTran The type of document transaction
     * @return List of documents of the specified type
     */
    //List<DocTranDePersonal> findByTipoDocTran(DocTranDePersonal.TipoDocTran tipoDocTran);
}
