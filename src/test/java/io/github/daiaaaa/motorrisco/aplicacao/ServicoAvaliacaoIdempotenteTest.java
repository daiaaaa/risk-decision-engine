package io.github.daiaaaa.motorrisco.aplicacao;

import io.github.daiaaaa.motorrisco.aplicacao.porta.ProvedorSinal;
import io.github.daiaaaa.motorrisco.aplicacao.porta.RegistroMetricas;
import io.github.daiaaaa.motorrisco.aplicacao.porta.RepositorioAvaliacoes;
import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
import io.github.daiaaaa.motorrisco.dominio.politica.CalculadoraRisco;
import io.github.daiaaaa.motorrisco.dominio.politica.PoliticaDecisao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServicoAvaliacaoIdempotenteTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger avaliacoesFeitas = new AtomicInteger();
    private final RepositorioEmMemoria repositorio = new RepositorioEmMemoria();
    private final List<String> publicadas = new CopyOnWriteArrayList<>();

    private final MetricasEmMemoria metricas = new MetricasEmMemoria();

    private final ServicoAvaliacaoIdempotente servico = new ServicoAvaliacaoIdempotente(
            avaliadorContandoChamadas(), repositorio,
            (transacao, avaliacao) -> publicadas.add(avaliacao.idAvaliacao()), metricas);

    @AfterEach
    void encerrar() {
        executor.close();
    }

    @Test
    void primeiraRequisicaoAvaliaEGrava() {
        ResultadoAvaliacao resultado = servico.avaliar(transacao("tx-1", "300.00"));

        assertThat(resultado.repetida()).isFalse();
        assertThat(avaliacoesFeitas).hasValue(1);
        assertThat(repositorio.dados).containsKey("tx-1");
        assertThat(publicadas).containsExactly(resultado.avaliacao().idAvaliacao());
        assertThat(metricas.emitidas).hasValue(1);
    }

    @Test
    void mesmaTransacaoDevolveAMesmaAvaliacaoSemAvaliarDeNovo() {
        ResultadoAvaliacao primeira = servico.avaliar(transacao("tx-1", "300.00"));
        ResultadoAvaliacao segunda = servico.avaliar(transacao("tx-1", "300.00"));

        assertThat(segunda.repetida()).isTrue();
        assertThat(segunda.avaliacao()).isEqualTo(primeira.avaliacao());
        assertThat(avaliacoesFeitas).hasValue(1);
        // O reenvio não gera um segundo evento nem conta como decisão nova.
        assertThat(publicadas).hasSize(1);
        assertThat(metricas.emitidas).hasValue(1);
        assertThat(metricas.repetidas).hasValue(1);
    }

    @Test
    void valorEscritoDeOutraFormaContaComoAMesmaTransacao() {
        servico.avaliar(transacao("tx-1", "300.00"));

        // 300 e 300.00 são o mesmo valor.
        assertThat(servico.avaliar(transacao("tx-1", "300")).repetida()).isTrue();
    }

    @Test
    void mesmoIdComDadosDiferentesGeraConflito() {
        servico.avaliar(transacao("tx-1", "300.00"));

        assertThatThrownBy(() -> servico.avaliar(transacao("tx-1", "9999.00")))
                .isInstanceOf(ConflitoIdempotenciaException.class)
                .hasMessageContaining("tx-1");
        assertThat(avaliacoesFeitas).hasValue(1);
        assertThat(publicadas).hasSize(1);
        assertThat(metricas.conflitos).hasValue(1);
    }

    @Test
    void naCorridaQuemGravouPrimeiroVence() {
        // Simula outra requisição que gravou entre o "buscar" e o "salvar se ausente" desta.
        Transacao transacao = transacao("tx-1", "300.00");
        AvaliacaoRisco daOutraRequisicao = new AvaliacaoRisco("avaliacao-da-outra", "tx-1", Decisao.APROVAR, 8,
                List.of(), Instant.parse("2026-11-10T12:00:00Z"), Duration.ofMillis(80));
        repositorio.buscarSempreVazio = true;
        repositorio.dados.put("tx-1", new AvaliacaoRegistrada(ImpressaoRequisicao.de(transacao), daOutraRequisicao));

        ResultadoAvaliacao resultado = servico.avaliar(transacao);

        assertThat(resultado.repetida()).isTrue();
        assertThat(resultado.avaliacao().idAvaliacao()).isEqualTo("avaliacao-da-outra");
        // Quem perdeu a corrida não publica nem conta como decisão nova.
        assertThat(publicadas).isEmpty();
        assertThat(metricas.emitidas).hasValue(0);
        assertThat(metricas.repetidas).hasValue(1);
    }

    private ServicoAvaliacaoRisco avaliadorContandoChamadas() {
        ProvedorSinal provedor = new ProvedorSinal() {
            public String nome() { return "dispositivo"; }
            public SinalRisco avaliar(Transacao t) {
                avaliacoesFeitas.incrementAndGet();
                return SinalRisco.pontuado("dispositivo", 5, 50, "dispositivo conhecido");
            }
        };
        return new ServicoAvaliacaoRisco(List.of(provedor), new CalculadoraRisco(),
                new PoliticaDecisao(40, 75), executor, Clock.systemUTC());
    }

    private static Transacao transacao(String id, String valor) {
        return new Transacao(id, "cliente-001", "dispositivo-abc", new BigDecimal(valor), TipoTransacao.PIX);
    }

    private static class MetricasEmMemoria implements RegistroMetricas {

        final AtomicInteger emitidas = new AtomicInteger();
        final AtomicInteger repetidas = new AtomicInteger();
        final AtomicInteger conflitos = new AtomicInteger();

        public void decisaoEmitida(AvaliacaoRisco avaliacao) { emitidas.incrementAndGet(); }
        public void respostaRepetida() { repetidas.incrementAndGet(); }
        public void conflitoIdempotencia() { conflitos.incrementAndGet(); }
    }

    /** Repositório em memória com a mesma semântica de "salvar se ausente" do Redis. */
    private static class RepositorioEmMemoria implements RepositorioAvaliacoes {

        final Map<String, AvaliacaoRegistrada> dados = new ConcurrentHashMap<>();
        boolean buscarSempreVazio;

        @Override
        public Optional<AvaliacaoRegistrada> buscar(String idTransacao) {
            return buscarSempreVazio ? Optional.empty() : Optional.ofNullable(dados.get(idTransacao));
        }

        @Override
        public AvaliacaoRegistrada salvarSeAusente(AvaliacaoRegistrada registro) {
            AvaliacaoRegistrada anterior = dados.putIfAbsent(registro.avaliacao().idTransacao(), registro);
            return anterior == null ? registro : anterior;
        }
    }
}
