package exotic.app.planta.repo.producto;

import exotic.app.planta.model.producto.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

@Repository
public interface ProductoRepo extends JpaRepository<Producto, String>, JpaSpecificationExecutor<Producto> {

    Optional<Producto> findByProductoId(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.productoId = :id")
    Optional<Producto> findByProductoIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.productoId in :ids order by p.productoId")
    List<Producto> findAllByProductoIdInForUpdate(@Param("ids") Collection<String> ids);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Producto p
               set p.costo = :nuevoCosto,
                   p.costoVersion = :nuevaVersion
             where p.productoId = :productoId
               and p.costoVersion = :versionEsperada
            """)
    int actualizarCostoSiVersion(
            @Param("productoId") String productoId,
            @Param("nuevoCosto") java.math.BigDecimal nuevoCosto,
            @Param("versionEsperada") long versionEsperada,
            @Param("nuevaVersion") long nuevaVersion);

    Optional<Producto> findByPrefijoLoteIgnoreCase(String prefijoLote);

}
