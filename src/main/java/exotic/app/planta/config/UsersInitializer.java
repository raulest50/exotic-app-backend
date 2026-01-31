package exotic.app.planta.config;

import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UsersInitializer {

    private final UserRepository userRepository;

    public void initializeUsers() {
        // Primero super_master (por encima de master), luego master
        userRepository.findByUsername("super_master").orElseGet(() -> {
            User superMaster = User.builder()
                    .cedula(2L)
                    .username("super_master")
                    .password(PasswordConfig.encodePassword("sm1243", "super_master"))
                    .estado(1)
                    .build();
            return userRepository.save(superMaster);
        });
        userRepository.findByUsername("master").orElseGet(() -> {
            String username = "master";
            String rawPassword = "m1243";
            User master = User.builder()
                    .cedula(1L)
                    .username(username)
                    .password(PasswordConfig.encodePassword(rawPassword, username))
                    .estado(1)
                    .build();
            return userRepository.save(master);
        });
    }
}
