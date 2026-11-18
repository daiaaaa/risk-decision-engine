package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;

import java.time.Instant;
import java.util.List;

/**
 * Contrato de saída da API. Fica separado do domínio para que o domínio possa mudar
 * sem quebrar os clientes da API.
 *
 * @param degradada verdadeiro quando pelo menos uma fonte não respondeu e seu sinal é um fallback
 */
public record RespostaAvaliacao(
        String idAvaliacao,
        String idTransacao,
        Decisao decisao,
        int pontuacao,
        boolean degradada,
        List<SinalResposta> sinais,
        Instant avaliadoEm,
        long tempoProcessamentoMs) {

    public record SinalResposta(String fonte, int pontuacao, int peso, boolean bloqueante,
                                boolean degradado, String motivo) {

        static SinalResposta de(SinalRisco sinal) {
            return new SinalResposta(sinal.fonte(), sinal.pontuacao(), sinal.peso(),
                    sinal.bloqueante(), sinal.degradado(), sinal.motivo());
        }
    }

    static RespostaAvaliacao de(AvaliacaoRisco avaliacao) {
        return new RespostaAvaliacao(
                avaliacao.idAvaliacao(),
                avaliacao.idTransacao(),
                avaliacao.decisao(),
                avaliacao.pontuacao(),
                avaliacao.sinais().stream().anyMatch(SinalRisco::degradado),
                avaliacao.sinais().stream().map(SinalResposta::de).toList(),
                avaliacao.avaliadoEm(),
                avaliacao.tempoProcessamento().toMillis());
    }
}
