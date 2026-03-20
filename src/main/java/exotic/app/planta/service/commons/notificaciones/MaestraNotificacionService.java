package exotic.app.planta.service.commons.notificaciones;

import exotic.app.planta.model.notificaciones.MaestraNotificacion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.notificaciones.MaestraNotificacionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaestraNotificacionService {

    private final MaestraNotificacionRepo maestraNotificacionRepo;
    private final UserRepository userRepository;

    public List<MaestraNotificacion> getAll() {
        return maestraNotificacionRepo.findAll();
    }

    @Transactional
    public MaestraNotificacion addUserToNotificacion(int notificacionId, Long userId) {
        MaestraNotificacion notificacion = maestraNotificacionRepo.findById(notificacionId)
                .orElseThrow(() -> new EntityNotFoundException("Notificacion no encontrada: " + notificacionId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + userId));

        boolean alreadyInGroup = notificacion.getUsersGroup().stream()
                .anyMatch(u -> u.getId().equals(userId));

        if (!alreadyInGroup) {
            notificacion.getUsersGroup().add(user);
            maestraNotificacionRepo.save(notificacion);
        }

        return notificacion;
    }

    @Transactional
    public MaestraNotificacion removeUserFromNotificacion(int notificacionId, Long userId) {
        MaestraNotificacion notificacion = maestraNotificacionRepo.findById(notificacionId)
                .orElseThrow(() -> new EntityNotFoundException("Notificacion no encontrada: " + notificacionId));

        notificacion.getUsersGroup().removeIf(u -> u.getId().equals(userId));
        maestraNotificacionRepo.save(notificacion);

        return notificacion;
    }
}
