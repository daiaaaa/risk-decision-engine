package io.github.daiaaaa.motorrisco.adaptador.saida.kafka;

import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Contrato do evento publicado no tópico {@value TopicosKafka#DECISOES}, serializado como JSON.
 *
 * <p>É um contrato público: outros times podem consumi-lo. Por isso tem um campo {@code versao};
 * uma mudança incompatível cria a versão 2, em vez de quebrar quem já consome a 1.
 */
public record EventoDecisaoRisco(
        int versao,
        String idAvaliacao,
        String idTransacao,
        String idCliente,
        BigDecimal valor,
        TipoTransacao tipo,
        Decisao decisao,
        int pontuacao,
        boolean degradada,
        Instant avaliadoEm) {

    public static final int VERSAO_ATUAL = 1;

    public EventoDecisaoRisco {
        Objects.requireNonNull(idAvaliacao, "idAvaliacao");
        Objects.requireNonNull(idTransacao, "idTransacao");
        Objects.requireNonNull(idCliente, "idCliente");
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(decisao, "decisao");
        Objects.requireNonNull(avaliadoEm, "avaliadoEm");
    }

    public static EventoDecisaoRisco de(Transacao transacao, AvaliacaoRisco avaliacao) {
        return new EventoDecisaoRisco(
                VERSAO_ATUAL,
                avaliacao.idAvaliacao(),
                avaliacao.idTransacao(),
                transacao.idCliente(),
                transacao.valor(),
                transacao.tipo(),
                avaliacao.decisao(),
                avaliacao.pontuacao(),
                avaliacao.sinais().stream().anyMatch(SinalRisco::degradado),
                avaliacao.avaliadoEm());
    }
}
