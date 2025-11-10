package tech.andrefsramos.course_scraper.adapters.outbound.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import tech.andrefsramos.course_scraper.core.ports.PlatformRepository;

import java.util.Optional;

/*
 * JpaPlatformRepositoryImpl

 * Finalidade

 * Implementação JPA do porto {@link PlatformRepository}, responsável por consultas
 * à entidade Platform (plataformas de cursos). A principal função é mapear nomes
 * de plataformas (como “evg”, “fgv”, “sebrae”) para seus respectivos IDs no banco.

 * Modelo de Dados

 * - PlatformEntity (tabela `platforms` ou similar)
 *   Campos esperados: id (PK), name (único, case-insensitive), baseUrl, enabled.
 */

@Repository
public class JpaPlatformRepositoryImpl implements PlatformRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaPlatformRepositoryImpl.class);

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<Long> findIdByNameIgnoreCase(String name) {
        if (name == null || name.isBlank()) {
            log.warn("[JPA] findIdByNameIgnoreCase chamado com nome vazio/nulo.");
            return Optional.empty();
        }

        long t0 = System.nanoTime();
        try {
            var q = em.createQuery(
                    "SELECT p.id FROM PlatformEntity p WHERE LOWER(p.name) = LOWER(:n)", Long.class);
            q.setParameter("n", name.trim());
            var list = q.getResultList();

            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.debug("[JPA] findIdByNameIgnoreCase nome='{}' resultados={} tookMs={}", name, list.size(), tookMs);

            if (list.size() > 1) {
                log.warn("[JPA] Múltiplas plataformas encontradas com nome='{}'. Verifique unicidade.", name);
            }

            if (!list.isEmpty()) {
                log.info("[JPA] Plataforma '{}' resolvida com id={}", name, list.get(0));
                return Optional.of(list.get(0));
            }

            log.debug("[JPA] Nenhuma plataforma encontrada para nome='{}'", name);
            return Optional.empty();

        } catch (Exception e) {
            log.error("[JPA] Falha ao buscar plataforma '{}' — cause={}", name, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
