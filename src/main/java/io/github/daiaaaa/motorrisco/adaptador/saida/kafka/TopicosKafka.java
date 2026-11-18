package io.github.daiaaaa.motorrisco.adaptador.saida.kafka;

public final class TopicosKafka {

    /** Uma mensagem por decisão de risco tomada. Chave: idCliente. */
    public static final String DECISOES = "decisoes-risco";

    /** Mensagens que o consumidor não conseguiu processar (dead letter queue). */
    public static final String DECISOES_DLQ = "decisoes-risco-dlq";

    private TopicosKafka() {
    }
}
