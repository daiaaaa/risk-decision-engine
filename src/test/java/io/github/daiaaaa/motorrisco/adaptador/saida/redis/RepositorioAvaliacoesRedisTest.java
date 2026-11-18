package io.github.daiaaaa.motorrisco.adaptador.saida.redis;

import io.github.daiaaaa.motorrisco.aplicacao.AvaliacaoRegistrada;
import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o adaptador contra um Redis de verdade, que o Testcontainers sobe em Docker.
 */
@Testcontainers
class RepositorioAvaliacoesRedisTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration VALIDADE = Duration.ofHours(24);

    private static LettuceConnectionFactory fabrica;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void conectar() {
        fabrica = fabrica(REDIS.getHost(), REDIS.getMappedPort(6379));
        redis = new StringRedisTemplate(fabrica);
    }

    @AfterAll
    static void desconectar() {
        fabrica.destroy();
    }

    private final RepositorioAvaliacoesRedis repositorio = new RepositorioAvaliacoesRedis(redis, JSON, VALIDADE);

    @Test
    void gravaEBuscaAMesmaAvaliacao() {
        AvaliacaoRegistrada registro = registro("impressao-a");

        repositorio.salvarSeAusente(registro);

        assertThat(repositorio.buscar(registro.avaliacao().idTransacao())).contains(registro);
    }

    @Test
    void segundaGravacaoNaoSobrescreveEDevolveAPrimeira() {
        AvaliacaoRegistrada primeira = registro("impressao-a");
        AvaliacaoRegistrada segunda = new AvaliacaoRegistrada("impressao-b",
                avaliacao(primeira.avaliacao().idTransacao(), "outra-avaliacao"));

        repositorio.salvarSeAusente(primeira);
        AvaliacaoRegistrada vencedora = repositorio.salvarSeAusente(segunda);

        assertThat(vencedora).isEqualTo(primeira);
        assertThat(repositorio.buscar(primeira.avaliacao().idTransacao())).contains(primeira);
    }

    @Test
    void chaveGravadaComValidade() {
        AvaliacaoRegistrada registro = registro("impressao-a");

        repositorio.salvarSeAusente(registro);

        Long segundosRestantes = redis.getExpire("avaliacao:" + registro.avaliacao().idTransacao());
        assertThat(segundosRestantes).isBetween(VALIDADE.toSeconds() - 60, VALIDADE.toSeconds());
    }

    @Test
    void idInexistenteNaoEncontraNada() {
        assertThat(repositorio.buscar("nao-existe-" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void redisForaDoArNaoDerrubaAAvaliacao() {
        // Porta 1: nada escuta ali, então toda chamada ao Redis falha.
        LettuceConnectionFactory fabricaQuebrada = fabrica("localhost", 1);
        try {
            RepositorioAvaliacoesRedis semRedis =
                    new RepositorioAvaliacoesRedis(new StringRedisTemplate(fabricaQuebrada), JSON, VALIDADE);
            AvaliacaoRegistrada registro = registro("impressao-a");

            assertThat(semRedis.buscar(registro.avaliacao().idTransacao())).isEmpty();
            assertThat(semRedis.salvarSeAusente(registro)).isEqualTo(registro);
        } finally {
            fabricaQuebrada.destroy();
        }
    }

    private static LettuceConnectionFactory fabrica(String host, int porta) {
        LettuceClientConfiguration cliente = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(500))
                .build();
        LettuceConnectionFactory nova = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, porta), cliente);
        nova.afterPropertiesSet();
        nova.start();
        return nova;
    }

    private static AvaliacaoRegistrada registro(String impressao) {
        return new AvaliacaoRegistrada(impressao, avaliacao("tx-" + UUID.randomUUID(), "avaliacao-1"));
    }

    private static AvaliacaoRisco avaliacao(String idTransacao, String idAvaliacao) {
        return new AvaliacaoRisco(idAvaliacao, idTransacao, Decisao.REVISAR, 40,
                List.of(SinalRisco.pontuado("dispositivo", 70, 50, "dispositivo nunca visto para este cliente"),
                        SinalRisco.indisponivel("historico-cliente", "tempo limite excedido após 200 ms")),
                Instant.parse("2026-11-10T12:00:00.123456Z"), Duration.ofMillis(81));
    }
}
