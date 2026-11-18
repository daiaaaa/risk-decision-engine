package io.github.daiaaaa.motorrisco.adaptador.saida.sinal;

import io.github.daiaaaa.motorrisco.aplicacao.porta.ProvedorSinal;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Impressão digital do dispositivo: uma transação vinda de um aparelho nunca visto
 * para este cliente é mais arriscada.
 */
@Component
public class ProvedorSinalDispositivo implements ProvedorSinal {

    static final String NOME = "dispositivo";
    private static final int PESO = 50;

    private static final Map<String, Set<String>> DISPOSITIVOS_CONHECIDOS = Map.of(
            "cliente-001", Set.of("dispositivo-abc"),
            "cliente-002", Set.of("dispositivo-xyz", "dispositivo-123"));

    private final Duration latencia;

    public ProvedorSinalDispositivo(@Value("${risco.sinais.dispositivo.latencia:50ms}") Duration latencia) {
        this.latencia = latencia;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public SinalRisco avaliar(Transacao transacao) {
        LatenciaSimulada.aguardar(latencia);
        boolean conhecido = DISPOSITIVOS_CONHECIDOS
                .getOrDefault(transacao.idCliente(), Set.of())
                .contains(transacao.idDispositivo());
        return conhecido
                ? SinalRisco.pontuado(NOME, 5, PESO, "dispositivo conhecido")
                : SinalRisco.pontuado(NOME, 70, PESO, "dispositivo nunca visto para este cliente");
    }
}
