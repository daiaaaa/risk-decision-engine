package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Contrato de saída da API. Fica separado do domínio para que o domínio possa mudar
 * sem quebrar os clientes da API.
 */
@Schema(description = "Resultado da avaliação de risco")
public record RespostaAvaliacao(
        @Schema(description = "Identificador desta avaliação; igual em um reenvio da mesma transação")
        String idAvaliacao,

        String idTransacao,

        @Schema(description = "APROVAR: segue. REVISAR: vai para um analista. NEGAR: recusada.")
        Decisao decisao,

        @Schema(description = "Risco de 0 (nenhum) a 100 (máximo), média ponderada dos sinais", example = "8")
        int pontuacao,

        @Schema(description = "Verdadeiro quando alguma fonte de sinais não respondeu; nesse caso nunca há aprovação automática")
        boolean degradada,

        @Schema(description = "A opinião de cada fonte de sinais, para auditoria")
        List<SinalResposta> sinais,

        Instant avaliadoEm,

        @Schema(description = "Tempo da avaliação original, em milissegundos", example = "81")
        long tempoProcessamentoMs) {

    @Schema(description = "Opinião de uma fonte de sinais")
    public record SinalResposta(
            @Schema(example = "dispositivo") String fonte,
            @Schema(description = "Risco de 0 a 100 segundo esta fonte", example = "5") int pontuacao,
            @Schema(description = "Peso na pontuação final; 0 = só atua como regra", example = "50") int peso,
            @Schema(description = "Verdadeiro = nega a transação independentemente da pontuação") boolean bloqueante,
            @Schema(description = "Verdadeiro = a fonte não respondeu; o sinal é um fallback") boolean degradado,
            @Schema(example = "dispositivo conhecido") String motivo) {

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
