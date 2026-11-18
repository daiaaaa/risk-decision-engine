package io.github.daiaaaa.motorrisco.configuracao;

import io.github.daiaaaa.motorrisco.adaptador.entrada.kafka.EventoInvalidoException;
import io.github.daiaaaa.motorrisco.adaptador.saida.kafka.PublicadorDecisoesKafka;
import io.github.daiaaaa.motorrisco.adaptador.saida.kafka.TopicosKafka;
import io.github.daiaaaa.motorrisco.adaptador.saida.memoria.FilaRevisaoEmMemoria;
import io.github.daiaaaa.motorrisco.aplicacao.ServicoRevisaoManual;
import io.github.daiaaaa.motorrisco.aplicacao.porta.FilaRevisao;
import io.github.daiaaaa.motorrisco.aplicacao.porta.PublicadorDecisoes;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.ExecutorService;

/**
 * Tópicos, publicação de eventos e tratamento de erros do consumidor.
 */
@Configuration
public class ConfiguracaoKafka {

    /** Três partições permitem até três consumidores do mesmo grupo em paralelo. */
    @Bean
    public NewTopic topicoDecisoes() {
        return TopicBuilder.name(TopicosKafka.DECISOES).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic topicoDecisoesDlq() {
        return TopicBuilder.name(TopicosKafka.DECISOES_DLQ).partitions(1).replicas(1).build();
    }

    /**
     * Quando o consumidor falha: tenta mais 2 vezes, com 1 s de intervalo, e depois envia a
     * mensagem para a DLQ, para não travar a partição. Erros que não se resolvem tentando de
     * novo (mensagem inválida) vão direto para a DLQ.
     */
    @Bean
    public CommonErrorHandler tratadorErrosConsumidor(KafkaTemplate<String, String> kafka) {
        DeadLetterPublishingRecoverer paraDlq = new DeadLetterPublishingRecoverer(kafka,
                // Partição negativa: o Kafka escolhe a partição da DLQ.
                (registro, erro) -> new TopicPartition(TopicosKafka.DECISOES_DLQ, -1));
        DefaultErrorHandler tratador = new DefaultErrorHandler(paraDlq, new FixedBackOff(1000L, 2L));
        tratador.addNotRetryableExceptions(EventoInvalidoException.class);
        return tratador;
    }

    @Bean
    public PublicadorDecisoes publicadorDecisoes(KafkaTemplate<String, String> kafka,
                                                 JsonMapper json,
                                                 ExecutorService executorSinais) {
        return new PublicadorDecisoesKafka(kafka, json, executorSinais);
    }

    @Bean
    public FilaRevisao filaRevisao() {
        return new FilaRevisaoEmMemoria();
    }

    @Bean
    public ServicoRevisaoManual servicoRevisaoManual(FilaRevisao fila) {
        return new ServicoRevisaoManual(fila);
    }
}
