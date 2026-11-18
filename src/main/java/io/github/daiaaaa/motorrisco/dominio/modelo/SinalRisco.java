package io.github.daiaaaa.motorrisco.dominio.modelo;

import java.util.Objects;

/**
 * A opinião de uma fonte de sinais sobre uma transação.
 *
 * @param fonte      nome da fonte que gerou o sinal
 * @param pontuacao  risco de 0 (nenhum) a 100 (máximo)
 * @param peso       quanto este sinal conta na pontuação final; 0 significa que ele só atua como regra
 * @param bloqueante quando verdadeiro, a transação é negada independentemente da pontuação
 * @param degradado  quando verdadeiro, a fonte não respondeu e este sinal é um fallback, não uma opinião real
 * @param motivo     explicação legível, útil para analistas e auditoria
 */
public record SinalRisco(String fonte, int pontuacao, int peso, boolean bloqueante, boolean degradado,
                         String motivo) {

    public SinalRisco {
        Objects.requireNonNull(fonte, "fonte");
        Objects.requireNonNull(motivo, "motivo");
        if (pontuacao < 0 || pontuacao > 100) {
            throw new IllegalArgumentException("a pontuação deve estar entre 0 e 100");
        }
        if (peso < 0) {
            throw new IllegalArgumentException("o peso não pode ser negativo");
        }
        if (bloqueante && degradado) {
            throw new IllegalArgumentException("um sinal degradado não pode ser bloqueante");
        }
    }

    public static SinalRisco pontuado(String fonte, int pontuacao, int peso, String motivo) {
        return new SinalRisco(fonte, pontuacao, peso, false, false, motivo);
    }

    public static SinalRisco bloqueio(String fonte, String motivo) {
        return new SinalRisco(fonte, 100, 0, true, false, motivo);
    }

    /**
     * Fallback usado quando a fonte falha, estoura o tempo limite ou está com o circuito aberto.
     * Peso 0 deixa o sinal fora da pontuação: não inventamos um risco que nunca recebemos.
     */
    public static SinalRisco indisponivel(String fonte, String motivo) {
        return new SinalRisco(fonte, 0, 0, false, true, motivo);
    }
}
