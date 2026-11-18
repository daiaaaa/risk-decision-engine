package io.github.daiaaaa.motorrisco.adaptador.saida.sinal;

import java.time.Duration;

/**
 * Simula o tempo de rede de uma chamada real (HTTP para um bureau, consulta a banco, modelo de ML).
 */
final class LatenciaSimulada {

    private LatenciaSimulada() {
    }

    static void aguardar(Duration latencia) {
        if (latencia.isZero() || latencia.isNegative()) {
            return;
        }
        try {
            Thread.sleep(latencia);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("avaliação do sinal interrompida", e);
        }
    }
}
