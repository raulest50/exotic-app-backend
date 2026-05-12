package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.PoolCapacidad;
import exotic.app.planta.model.producto.dto.PoolCapacidadDTO;
import exotic.app.planta.model.producto.dto.PoolCapacidadUpsertRequestDTO;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.PoolCapacidadRepo;
import exotic.app.planta.resource.productos.exceptions.PoolCapacidadNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class PoolCapacidadService {

    private final PoolCapacidadRepo poolCapacidadRepo;
    private final CategoriaRepo categoriaRepo;

    @Transactional(readOnly = true)
    public List<PoolCapacidadDTO> getAll() {
        return poolCapacidadRepo.findAll(Sort.by(Sort.Direction.ASC, "nombre")).stream()
                .map(PoolCapacidadDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public PoolCapacidadDTO getById(int id) {
        return PoolCapacidadDTO.fromEntity(findEntityById(id));
    }

    public PoolCapacidadDTO create(PoolCapacidadUpsertRequestDTO request) {
        PoolCapacidad pool = new PoolCapacidad();
        applyRequest(pool, request, true);
        return PoolCapacidadDTO.fromEntity(poolCapacidadRepo.save(pool));
    }

    public PoolCapacidadDTO update(int id, PoolCapacidadUpsertRequestDTO request) {
        PoolCapacidad pool = findEntityById(id);
        applyRequest(pool, request, false);
        return PoolCapacidadDTO.fromEntity(poolCapacidadRepo.save(pool));
    }

    public void delete(int id) {
        PoolCapacidad pool = findEntityById(id);
        long categoriasAsociadas = categoriaRepo.countByPoolCapacidad_Id(pool.getId());
        if (categoriasAsociadas > 0) {
            throw new IllegalStateException(
                    "No se puede eliminar el pool de capacidad porque esta asignado a " +
                            categoriasAsociadas + " categoria(s)."
            );
        }
        poolCapacidadRepo.delete(pool);
    }

    private PoolCapacidad findEntityById(int id) {
        return poolCapacidadRepo.findById(id)
                .orElseThrow(() -> new PoolCapacidadNotFoundException("No se encontro pool de capacidad con ID: " + id));
    }

    private void applyRequest(PoolCapacidad pool, PoolCapacidadUpsertRequestDTO request, boolean isCreate) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud del pool de capacidad no puede ser nula");
        }

        String nombre = request.getNombre() != null ? request.getNombre().trim() : "";
        if (nombre.isEmpty()) {
            throw new IllegalArgumentException("El nombre del pool de capacidad no puede estar vacio");
        }

        Integer capacidadDiaria = request.getCapacidadDiaria();
        if (capacidadDiaria == null || capacidadDiaria < 0) {
            throw new IllegalArgumentException("La capacidad diaria debe ser mayor o igual a 0");
        }

        boolean duplicateName = poolCapacidadRepo.existsByNombreIgnoreCase(nombre)
                && (isCreate || !nombre.equalsIgnoreCase(pool.getNombre()));
        if (duplicateName) {
            throw new IllegalArgumentException("Ya existe un pool de capacidad con el nombre: " + nombre);
        }

        pool.setNombre(nombre);
        pool.setCapacidadDiaria(capacidadDiaria);
        pool.setDescripcion(request.getDescripcion() != null ? request.getDescripcion().trim() : null);
        pool.setActivo(request.getActivo() != null ? request.getActivo() : pool.isActivo());
    }
}
