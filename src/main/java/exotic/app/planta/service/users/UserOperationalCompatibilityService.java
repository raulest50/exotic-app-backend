package exotic.app.planta.service.users;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.dto.UserAssignmentStatusDTO;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserOperationalCompatibilityService {

    private final UserRepository userRepository;
    private final AreaProduccionRepo areaProduccionRepo;

    /**
     * Regla de negocio centralizada: un usuario pertenece a exactamente uno de dos mundos,
     * o tiene accesos a modulos/tabs o es responsable de un area operativa.
     * Todas las validaciones de compatibilidad pasan por este servicio para evitar
     * duplicar reglas entre el modulo de usuarios, areas operativas y autenticacion.
     */
    public UserAssignmentStatusDTO buildAssignmentStatus(Long userId, Integer excludeAreaId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<AreaOperativa> assignedAreas = areaProduccionRepo.findAllByResponsableArea_Id(userId);
        AreaOperativa primaryArea = assignedAreas.isEmpty() ? null : assignedAreas.get(0);
        boolean hasModuloAccesos = user.getModuloAccesos() != null && !user.getModuloAccesos().isEmpty();
        boolean isAreaResponsable = primaryArea != null;
        boolean hasOtherAreaAssignment = excludeAreaId == null
                ? isAreaResponsable
                : areaProduccionRepo.existsByResponsableArea_IdAndAreaIdNot(userId, excludeAreaId);

        return UserAssignmentStatusDTO.builder()
                .areaResponsable(isAreaResponsable)
                .areaResponsableId(primaryArea != null ? primaryArea.getAreaId() : null)
                .areaResponsableNombre(primaryArea != null ? primaryArea.getNombre() : null)
                .hasModuloAccesos(hasModuloAccesos)
                .canReceiveModuloAccesos(!isAreaResponsable)
                .canBeAreaResponsable(!hasModuloAccesos && !hasOtherAreaAssignment)
                .build();
    }

    public boolean isAreaResponsable(Long userId) {
        return areaProduccionRepo.existsByResponsableArea_Id(userId);
    }

    public boolean hasModuloAccesos(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getModuloAccesos() != null && !user.getModuloAccesos().isEmpty();
    }

    public void assertCanReceiveModuloAccesos(Long userId) {
        UserAssignmentStatusDTO status = buildAssignmentStatus(userId, null);
        if (!status.canReceiveModuloAccesos()) {
            throw new RuntimeException(
                    "No se pueden asignar permisos de modulos a un usuario responsable del area "
                            + status.getAreaResponsableNombre()
                            + "."
            );
        }
    }

    public void assertCanBeAreaResponsable(Long userId, Integer excludeAreaId) {
        UserAssignmentStatusDTO status = buildAssignmentStatus(userId, excludeAreaId);
        if (status.hasModuloAccesos()) {
            throw new RuntimeException("No se puede asignar como responsable a un usuario que ya tiene accesos a modulos.");
        }
        if (excludeAreaId == null && status.isAreaResponsable()) {
            throw new RuntimeException("El usuario ya es responsable del area " + status.getAreaResponsableNombre() + ".");
        }
        if (excludeAreaId != null && !status.canBeAreaResponsable()) {
            throw new RuntimeException("El usuario ya es responsable de otra area operativa.");
        }
    }
}
