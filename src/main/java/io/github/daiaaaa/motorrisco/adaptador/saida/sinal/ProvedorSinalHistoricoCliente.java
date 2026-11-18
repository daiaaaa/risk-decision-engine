package io.github.daiaaaa.motorrisco.adaptador.saida.sinal;

import io.github.daiaaaa.motorrisco.aplicacao.porta.ProvedorSinal;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Sinal comportamental: compara o valor com o valor médio das transações do cliente.
 */
@Component
public class ProvedorSinalHistoricoCliente implements ProvedorSinal {

    static final String NOME = "historico-cliente";
    private static final int PESO = 50;

    private static final Map<String, BigDecimal> VALOR_MEDIO = Map.of(
            "cliente-001", new BigDecimal("500.00"),
            "cliente-002", new BigDecimal("2000.00"));

    private static final BigDecimal RAZAO_ALTA = BigDecimal.valueOf(5);
    private static final BigDecimal RAZAO_MEDIA = BigDecimal.valueOf(2);

    private final Duration latencia;

    public ProvedorSinalHistoricoCliente(@Value("${risco.sinais.historico.latencia:80ms}") Duration latencia) {
        this.latencia = latencia;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public SinalRisco avaliar(Transacao transacao) {
        LatenciaSimulada.aguardar(latencia);
        BigDecimal media = VALOR_MEDIO.get(transacao.idCliente());
        if (media == null) {
            return SinalRisco.pontuado(NOME, 60, PESO, "cliente sem histórico de transações");
        }
        BigDecimal valor = transacao.valor();
        if (valor.compareTo(media.multiply(RAZAO_ALTA)) > 0) {
            return SinalRisco.pontuado(NOME, 85, PESO, "valor acima de 5x a média do cliente");
        }
        if (valor.compareTo(media.multiply(RAZAO_MEDIA)) > 0) {
            return SinalRisco.pontuado(NOME, 45, PESO, "valor acima de 2x a média do cliente");
        }
        return SinalRisco.pontuado(NOME, 10, PESO, "valor dentro do padrão do cliente");
    }
}
