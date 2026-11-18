package io.github.daiaaaa.motorrisco.configuracao;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configurações de resiliência aplicadas a todas as fontes de sinais.
 *
 * @param tempoLimite          tempo máximo que uma fonte pode levar
 * @param tamanhoJanela        quantidade de chamadas recentes que o circuit breaker observa
 * @param minimoChamadas       chamadas necessárias antes de avaliar a taxa de falha
 * @param limiteTaxaFalha      percentual de falhas que abre o circuito
 * @param esperaCircuitoAberto quanto tempo o circuito fica aberto antes de testar a fonte de novo
 * @param chamadasSemiAberto   chamadas de teste permitidas enquanto a fonte se recupera
 */
@ConfigurationProperties(prefix = "risco.resiliencia")
public record PropriedadesResiliencia(
        Duration tempoLimite,
        int tamanhoJanela,
        int minimoChamadas,
        float limiteTaxaFalha,
        Duration esperaCircuitoAberto,
        int chamadasSemiAberto) {
}
