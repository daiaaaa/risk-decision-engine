package io.github.daiaaaa.motorrisco.aplicacao;

import io.github.daiaaaa.motorrisco.aplicacao.porta.ProvedorSinal;
import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
import io.github.daiaaaa.motorrisco.dominio.politica.CalculadoraRisco;
import io.github.daiaaaa.motorrisco.dominio.politica.PoliticaDecisao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Orquestra uma avaliação de risco: dispara todas as fontes de sinais em paralelo (fan-out),
 * junta os resultados (fan-in), calcula a pontuação e aplica a política de decisão.
 *
 * <p>Esta classe assume que as fontes não lançam exceção nem travam. Essa garantia vem do
 * decorator de resiliência (tempo limite, circuit breaker e fallback) aplicado a cada fonte
 * na configuração da aplicação; o orquestrador continua simples.
 */
public class ServicoAvaliacaoRisco {

    private final List<ProvedorSinal> provedores;
    private final CalculadoraRisco calculadora;
    private final PoliticaDecisao politica;
    private final Executor executor;
    private final Clock relogio;

    public ServicoAvaliacaoRisco(List<ProvedorSinal> provedores,
                                 CalculadoraRisco calculadora,
                                 PoliticaDecisao politica,
                                 Executor executor,
                                 Clock relogio) {
        if (provedores.isEmpty()) {
            throw new IllegalArgumentException("é necessário pelo menos um ProvedorSinal");
        }
        this.provedores = List.copyOf(provedores);
        this.calculadora = calculadora;
        this.politica = politica;
        this.executor = executor;
        this.relogio = relogio;
    }

    public AvaliacaoRisco avaliar(Transacao transacao) {
        long inicio = System.nanoTime();

        // Fan-out: dispara todas as fontes ao mesmo tempo.
        List<CompletableFuture<SinalRisco>> futuros = provedores.stream()
                .map(provedor -> CompletableFuture.supplyAsync(
                        () -> provedor.avaliar(transacao), executor))
                .toList();

        // Fan-in: a latência total é a da fonte mais lenta, não a soma de todas.
        List<SinalRisco> sinais = futuros.stream()
                .map(CompletableFuture::join)
                .toList();

        int pontuacao = calculadora.calcular(sinais);
        Decisao decisao = politica.decidir(pontuacao, sinais);

        return new AvaliacaoRisco(
                UUID.randomUUID().toString(),
                transacao.idTransacao(),
                decisao,
                pontuacao,
                sinais,
                Instant.now(relogio),
                Duration.ofNanos(System.nanoTime() - inicio));
    }
}
