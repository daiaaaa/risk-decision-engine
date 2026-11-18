package io.github.daiaaaa.motorrisco.adaptador.saida.redis;

import io.github.daiaaaa.motorrisco.aplicacao.AvaliacaoRegistrada;
import io.github.daiaaaa.motorrisco.aplicacao.porta.RepositorioAvaliacoes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * Guarda as avaliações no Redis como JSON, na chave {@code avaliacao:<idTransacao>},
 * com validade (TTL) para que o Redis não cresça sem limite.
 *
 * <p>Se o Redis estiver fora do ar, a avaliação continua sem idempotência: parar a antifraude
 * porque o cache caiu seria pior do que, no pior caso, avaliar de novo uma transação repetida.
 */
public class RepositorioAvaliacoesRedis implements RepositorioAvaliacoes {

    private static final Logger log = LoggerFactory.getLogger(RepositorioAvaliacoesRedis.class);
    private static final String PREFIXO = "avaliacao:";

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Duration validade;

    public RepositorioAvaliacoesRedis(StringRedisTemplate redis, JsonMapper json, Duration validade) {
        this.redis = redis;
        this.json = json;
        this.validade = validade;
    }

    @Override
    public Optional<AvaliacaoRegistrada> buscar(String idTransacao) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIXO + idTransacao))
                    .map(valor -> json.readValue(valor, AvaliacaoRegistrada.class));
        } catch (DataAccessException e) {
            log.warn("Redis indisponível ao buscar {}; seguindo sem idempotência: {}", idTransacao, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public AvaliacaoRegistrada salvarSeAusente(AvaliacaoRegistrada registro) {
        String chave = PREFIXO + registro.avaliacao().idTransacao();
        try {
            // SET chave valor NX EX validade: grava só se a chave não existir, de forma atômica.
            Boolean gravou = redis.opsForValue().setIfAbsent(chave, json.writeValueAsString(registro), validade);
            if (Boolean.TRUE.equals(gravou)) {
                return registro;
            }
            String existente = redis.opsForValue().get(chave);
            return existente == null ? registro : json.readValue(existente, AvaliacaoRegistrada.class);
        } catch (DataAccessException e) {
            log.warn("Redis indisponível ao gravar {}; seguindo sem idempotência: {}", chave, e.getMessage());
            return registro;
        }
    }
}
