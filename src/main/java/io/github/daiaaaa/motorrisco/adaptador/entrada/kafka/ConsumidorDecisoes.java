package io.github.daiaaaa.motorrisco.adaptador.entrada.kafka;

import io.github.daiaaaa.motorrisco.adaptador.saida.kafka.EventoDecisaoRisco;
import io.github.daiaaaa.motorrisco.adaptador.saida.kafka.TopicosKafka;
import io.github.daiaaaa.motorrisco.aplicacao.ServicoRevisaoManual;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consome as decisões publicadas e encaminha as que precisam de analista para a revisão manual.
 *
 * <p>Num sistema real, este consumidor ficaria em outro serviço (o de revisão). Aqui ele mora na
 * mesma aplicação para manter o projeto simples, mas só conversa com o produtor pelo tópico.
 *
 * <p>Se o processamento falhar, o tratador de erros configurado em {@code ConfiguracaoKafka}
 * tenta de novo e, esgotadas as tentativas, envia a mensagem para a DLQ.
 */
@Component
public class ConsumidorDecisoes {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorDecisoes.class);

    private final ServicoRevisaoManual servico;
    private final JsonMapper json;

    public ConsumidorDecisoes(ServicoRevisaoManual servico, JsonMapper json) {
        this.servico = servico;
        this.json = json;
    }

    @KafkaListener(topics = TopicosKafka.DECISOES)
    public void receber(String mensagem) {
        EventoDecisaoRisco evento = ler(mensagem);
        boolean nova = servico.receberDecisao(evento.decisao(), paraRevisao(evento));
        if (nova) {
            log.info("Transação {} encaminhada para revisão manual", evento.idTransacao());
        }
    }

    EventoDecisaoRisco ler(String mensagem) {
        EventoDecisaoRisco evento;
        try {
            evento = json.readValue(mensagem, EventoDecisaoRisco.class);
        } catch (JacksonException e) {
            throw new EventoInvalidoException("mensagem não é um EventoDecisaoRisco válido", e);
        }
        if (evento.versao() != EventoDecisaoRisco.VERSAO_ATUAL) {
            throw new EventoInvalidoException("versão de evento não suportada: " + evento.versao());
        }
        return evento;
    }

    private static RevisaoPendente paraRevisao(EventoDecisaoRisco e) {
        return new RevisaoPendente(e.idAvaliacao(), e.idTransacao(), e.idCliente(), e.valor(), e.tipo(),
                e.pontuacao(), e.degradada(), e.avaliadoEm());
    }
}
