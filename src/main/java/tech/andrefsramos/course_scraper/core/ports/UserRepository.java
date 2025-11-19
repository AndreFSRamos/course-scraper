package tech.andrefsramos.course_scraper.core.ports;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.UserEntity;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
