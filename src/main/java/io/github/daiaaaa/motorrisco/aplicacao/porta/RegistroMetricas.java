package io.github.daiaaaa.motorrisco.aplicacao.porta;

import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;

/**
 * Porta de saída: métricas de negócio do motor. A aplicação diz o que aconteceu; o adaptador
 * decide como medir (hoje, Micrometer e Prometheus).
 */
public interface RegistroMetricas {

    /** Uma avaliação nova foi feita e gravada. Reenvios não contam. */
    void decisaoEmitida(AvaliacaoRisco avaliacao);

    /** Um reenvio recebeu a avaliação guardada, sem nova avaliação. */
    void respostaRepetida();

    /** Um idTransacao foi reaproveitado com dados diferentes (409). */
    void conflitoIdempotencia();

    /** Implementação que não mede nada, útil em testes. */
    RegistroMetricas NENHUM = new RegistroMetricas() {
        public void decisaoEmitida(AvaliacaoRisco avaliacao) { }
        public void respostaRepetida() { }
        public void conflitoIdempotencia() { }
    };
}
