package exotic.app.planta.repo.producto.costos;

import exotic.app.planta.model.producto.costos.CargaCostosLote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CargaCostosLoteRepo extends JpaRepository<CargaCostosLote, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CargaCostosLote l where l.id = :id")
    Optional<CargaCostosLote> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("""
            update CargaCostosLote l
               set l.estado = :expirado,
                   l.tokenHash = null,
                   l.tokenExpiraEn = null
             where l.id = :id
               and l.usuario.id = :usuarioId
               and l.estado = :preparado
               and l.expiraEn <= :ahora
            """)
    int expirarSiCorresponde(
            @Param("id") UUID id,
            @Param("usuarioId") Long usuarioId,
            @Param("ahora") java.time.LocalDateTime ahora,
            @Param("preparado") CargaCostosLote.Estado preparado,
            @Param("expirado") CargaCostosLote.Estado expirado);

    @Modifying
    @Query("""
            update CargaCostosLote l
               set l.estado = :expirado,
                   l.tokenHash = null,
                   l.tokenExpiraEn = null
             where l.estado = :preparado
               and l.expiraEn <= :ahora
            """)
    int expirarVencidos(
            @Param("ahora") java.time.LocalDateTime ahora,
            @Param("preparado") CargaCostosLote.Estado preparado,
            @Param("expirado") CargaCostosLote.Estado expirado);

    @Modifying
    @Query("delete from CargaCostosLote l where l.estado in :estados and l.creadoEn < :limite")
    int eliminarTerminalesAntiguos(
            @Param("estados") java.util.Collection<CargaCostosLote.Estado> estados,
            @Param("limite") java.time.LocalDateTime limite);
}
