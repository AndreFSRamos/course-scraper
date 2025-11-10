package tech.andrefsramos.course_scraper.core.application.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.andrefsramos.course_scraper.core.application.QueryLatestCoursesUseCase;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.CourseRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/*
 * Finalidade

 * Serviço de leitura que lista cursos mais recentes a partir de filtros opcionais:
 * plataforma, área, somente gratuitos, data de corte ("since"), e paginação (page/size).
 * Encapsula o acesso ao {@link CourseRepository}, adicionando:
 *  - Normalização de parâmetros (trim, vazio->null, clamps de page/size).
 *  - Logs estruturados para rastreabilidade e diagnóstico.
 *  - Medição de tempo de execução.
 *  - Tratamento defensivo de exceções (retorna lista vazia em falha).
 */
public class QueryLatestCoursesService implements QueryLatestCoursesUseCase {

    private static final Logger log = LoggerFactory.getLogger(QueryLatestCoursesService.class);

    private final CourseRepository courseRepository;

    public QueryLatestCoursesService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public List<Course> list(String platform, String area, boolean freeOnly, Instant since, int page, int size) {
        final long t0 = System.nanoTime();

        String p = normalize(platform);
        String a = normalize(area);

        int effPage = Math.max(page, 0);
        int effSize = clamp(size, 1, 100);

        boolean clampedPage = effPage != page;
        boolean clampedSize = effSize != size;

        if (clampedPage || clampedSize) {
            log.warn("[Query] Parâmetros de paginação ajustados: page {}->{} | size {}->{}",
                    page, effPage, size, effSize);
        }

        if (log.isDebugEnabled()) {
            log.debug("[Query] Filtros: platform='{}', area='{}', freeOnly={}, since={}, page={}, size={}",
                    p, a, freeOnly, since, effPage, effSize);
        } else {
            log.info("[Query] Iniciando listagem (page={}, size={}, freeOnly={})", effPage, effSize, freeOnly);
        }

        try {
            List<Course> result = courseRepository.findLatest(p, a, freeOnly, since, effPage, effSize);

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            log.info("[Query] OK: retornados={} duração={} ms (platform='{}', area='{}')",
                    result.size(), elapsedMs, p, a);

            if (log.isDebugEnabled()) {
                log.debug("[Query] Exemplos: {}", result.stream()
                        .limit(5)
                        .map(c -> String.format(Locale.ROOT, "#%s|%s",
                                c.id() == null ? "-" : c.id().toString(),
                                safeEllipsis(c.title(), 60)))
                        .toList());
            }

            return result;
        } catch (Exception e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            log.error("[Query] ERRO após {} ms: {}", elapsedMs, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String safeEllipsis(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
