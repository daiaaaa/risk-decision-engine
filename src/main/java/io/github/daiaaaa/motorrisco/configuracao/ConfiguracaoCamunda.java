package io.github.daiaaaa.motorrisco.configuracao;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.Deployment;
import io.github.daiaaaa.motorrisco.adaptador.saida.camunda.FilaRevisaoCamunda;
import io.github.daiaaaa.motorrisco.adaptador.saida.camunda.TrabalhadorRegistrarDecisaoFinal;
import io.github.daiaaaa.motorrisco.aplicacao.porta.FilaRevisao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

/**
 * Liga a revisão manual ao Camunda 8: o adaptador {@link FilaRevisao} publica a mensagem que
 * inicia o processo "revisao-manual" (recursos/bpmn), e o job worker registra o resultado.
 *
 * <p>Ativa com {@code risco.revisao.motor=camunda}. O
 * {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty} evita abrir
 * uma conexão gRPC com o Zeebe quando o motor em memória está em uso (ex.: testes que não
 * envolvem o processo).
 *
 * <p>{@code CamundaClient} é injetado como {@code @Lazy}: nos testes com
 * {@code @CamundaSpringProcessTest}, o client "de verdade" (apontando para o Camunda do teste,
 * via Testcontainers) só existe depois que o contexto Spring já subiu. Resolver o client no
 * primeiro uso, em vez de na criação deste bean, evita capturar o client errado.
 *
 * <p>{@code @Deployment} implanta o BPMN quando o client do processo (de produção ou do
 * CamundaSpringProcessTest) fica disponível; é mais confiável nos testes do que a propriedade
 * {@code camunda.client.deployment.resources}, que roda contra o client eager do startup.
 */
@Configuration
@ConditionalOnProperty(name = "risco.revisao.motor", havingValue = "camunda")
@Deployment(resources = "classpath:bpmn/*.bpmn")
public class ConfiguracaoCamunda {

    @Bean
    public FilaRevisao filaRevisaoCamunda(@Lazy CamundaClient camunda,
                                          @Value("${risco.revisao.prazo}") Duration prazo) {
        return new FilaRevisaoCamunda(camunda, prazo);
    }

    @Bean
    public TrabalhadorRegistrarDecisaoFinal trabalhadorRegistrarDecisaoFinal() {
        return new TrabalhadorRegistrarDecisaoFinal();
    }
}
