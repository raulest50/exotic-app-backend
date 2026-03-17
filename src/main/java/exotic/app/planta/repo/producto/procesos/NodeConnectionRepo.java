package exotic.app.planta.repo.producto.procesos;

import exotic.app.planta.model.producto.manufacturing.procesos.nodo.NodeConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface NodeConnectionRepo extends JpaRepository<NodeConnection, Long> {

    @Modifying
    @Query("DELETE FROM NodeConnection nc WHERE nc.sourceHandle.node.pNodeId IN :nodeIds OR nc.targetHandle.node.pNodeId IN :nodeIds")
    void deleteByNodeIds(@Param("nodeIds") Collection<Long> nodeIds);
}
