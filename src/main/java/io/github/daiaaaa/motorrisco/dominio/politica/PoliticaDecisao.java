package io.github.daiaaaa.motorrisco.dominio.politica;

import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;

import java.util.List;

/**
 * Transforma a pontuação em decisão. As regras são aplicadas nesta ordem:
 *
 * <ol>
 *   <li>Qualquer sinal bloqueante -&gt; NEGAR.</li>
 *   <li>Limiares de pontuação:
 *     <pre>
 *       pontuacao &lt; limiarRevisao                  -&gt; APROVAR
 *       limiarRevisao &lt;= pontuacao &lt; limiarNegacao -&gt; REVISAR
 *       pontuacao &gt;= limiarNegacao                  -&gt; NEGAR
 *     </pre>
 *   </li>
 *   <li>Fail-safe: se algum sinal estiver degradado, APROVAR vira REVISAR. Nunca aprovamos
 *       automaticamente uma transação que não foi totalmente verificada.</li>
 * </ol>
 */
public class PoliticaDecisao {

    private final int limiarRevisao;
    private final int limiarNegacao;

    public PoliticaDecisao(int limiarRevisao, int limiarNegacao) {
        if (limiarRevisao < 0 || limiarNegacao > 100 || limiarRevisao >= limiarNegacao) {
            throw new IllegalArgumentException(
                    "esperado 0 <= limiarRevisao < limiarNegacao <= 100");
        }
        this.limiarRevisao = limiarRevisao;
        this.limiarNegacao = limiarNegacao;
    }

    public Decisao decidir(int pontuacao, List<SinalRisco> sinais) {
        if (sinais.stream().anyMatch(SinalRisco::bloqueante)) {
            return Decisao.NEGAR;
        }
        Decisao pelaPontuacao = pelaPontuacao(pontuacao);
        if (pelaPontuacao == Decisao.APROVAR && sinais.stream().anyMatch(SinalRisco::degradado)) {
            return Decisao.REVISAR;
        }
        return pelaPontuacao;
    }

    private Decisao pelaPontuacao(int pontuacao) {
        if (pontuacao >= limiarNegacao) {
            return Decisao.NEGAR;
        }
        if (pontuacao >= limiarRevisao) {
            return Decisao.REVISAR;
        }
        return Decisao.APROVAR;
    }
}
