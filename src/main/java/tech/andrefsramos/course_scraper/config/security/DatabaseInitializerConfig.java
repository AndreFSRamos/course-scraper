package tech.andrefsramos.course_scraper.config.security;


import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.UserEntity;
import tech.andrefsramos.course_scraper.core.domain.Role;
import tech.andrefsramos.course_scraper.core.ports.UserRepository;


@Configuration
@RequiredArgsConstructor
public class DatabaseInitializerConfig {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initDefaultUsers() {
        return args -> {
            if (!userRepository.existsByUsername("admin")) {
                UserEntity admin = new UserEntity();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin"));
                admin.setRole(Role.ADMIN);
                admin.setEnabled(true);
                userRepository.save(admin);
            }

            if (!userRepository.existsByUsername("admin.collector")) {
                UserEntity collector = new UserEntity();
                collector.setUsername("admin.collector");
                collector.setPassword(passwordEncoder.encode("admin.collector"));
                collector.setRole(Role.COLLECTOR);
                collector.setEnabled(true);
                userRepository.save(collector);
            }
        };
    }
}
