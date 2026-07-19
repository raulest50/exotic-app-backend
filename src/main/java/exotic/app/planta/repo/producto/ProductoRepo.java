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

@Repository
public interface ProductoRepo extends JpaRepository<Producto, String>, JpaSpecificationExecutor<Producto> {

    Optional<Producto> findByProductoId(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.productoId = :id")
    Optional<Producto> findByProductoIdForUpdate(@Param("id") String id);

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
