package io.github.daiaaaa.motorrisco.configuracao;

import io.github.daiaaaa.motorrisco.adaptador.saida.memoria.FilaRevisaoEmMemoria;
import io.github.daiaaaa.motorrisco.aplicacao.ServicoRevisaoManual;
import io.github.daiaaaa.motorrisco.aplicacao.porta.FilaRevisao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Revisão manual. O adaptador da porta {@link FilaRevisao} é escolhido por
 * {@code risco.revisao.motor}:
 * <ul>
 *   <li>{@code camunda}: processo BPMN no Camunda 8 (ver {@link ConfiguracaoCamunda}).</li>
 *   <li>{@code memoria}: fila em memória, sem prazo; usada nos testes que não envolvem o processo.</li>
 * </ul>
 * O consumidor Kafka e a API não mudam: só o adaptador é trocado.
 */
@Configuration
public class ConfiguracaoRevisao {

    @Bean
    @ConditionalOnProperty(name = "risco.revisao.motor", havingValue = "memoria")
    public FilaRevisao filaRevisaoEmMemoria() {
        return new FilaRevisaoEmMemoria();
    }

    @Bean
    public ServicoRevisaoManual servicoRevisaoManual(FilaRevisao fila) {
        return new ServicoRevisaoManual(fila);
    }
}
