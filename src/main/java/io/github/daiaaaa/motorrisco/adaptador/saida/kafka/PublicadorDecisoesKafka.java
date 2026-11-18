package io.github.daiaaaa.motorrisco.adaptador.saida.kafka;

import io.github.daiaaaa.motorrisco.aplicacao.porta.PublicadorDecisoes;
import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.Executor;

/**
 * Publica cada decisão no tópico {@value TopicosKafka#DECISOES}.
 *
 * <ul>
 *   <li><b>Chave = idCliente</b>: as decisões de um mesmo cliente caem na mesma partição, então
 *       quem consome as recebe na ordem em que foram publicadas.</li>
 *   <li><b>Fora da thread da requisição</b>: o envio roda em outra virtual thread, para que um
 *       Kafka lento ou fora do ar não atrase a resposta da API.</li>
 *   <li><b>Limitação conhecida</b>: se o Kafka estiver fora do ar, o evento se perde (fica só no
 *       log). A solução de mercado é o padrão Transactional Outbox.</li>
 * </ul>
 */
public class PublicadorDecisoesKafka implements PublicadorDecisoes {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDecisoesKafka.class);

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final Executor executor;

    public PublicadorDecisoesKafka(KafkaTemplate<String, String> kafka, JsonMapper json, Executor executor) {
        this.kafka = kafka;
        this.json = json;
        this.executor = executor;
    }

    @Override
    public void publicar(Transacao transacao, AvaliacaoRisco avaliacao) {
        try {
            String mensagem = json.writeValueAsString(EventoDecisaoRisco.de(transacao, avaliacao));
            executor.execute(() -> enviar(transacao.idCliente(), mensagem, avaliacao.idAvaliacao()));
        } catch (RuntimeException e) {
            log.error("Não foi possível preparar o evento da avaliação {}", avaliacao.idAvaliacao(), e);
        }
    }

    private void enviar(String chave, String mensagem, String idAvaliacao) {
        try {
            kafka.send(TopicosKafka.DECISOES, chave, mensagem).whenComplete((resultado, erro) -> {
                if (erro != null) {
                    log.error("Falha ao publicar a avaliação {}: {}", idAvaliacao, erro.getMessage());
                } else {
                    log.debug("Avaliação {} publicada na partição {}, offset {}", idAvaliacao,
                            resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset());
                }
            });
        } catch (RuntimeException e) {
            log.error("Falha ao publicar a avaliação {}: {}", idAvaliacao, e.getMessage());
        }
    }
}
