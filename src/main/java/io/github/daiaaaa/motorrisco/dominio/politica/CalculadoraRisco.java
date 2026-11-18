package io.github.daiaaaa.motorrisco.dominio.politica;

import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;

import java.util.List;

/**
 * Combina os sinais em uma única pontuação usando média ponderada.
 * Sinais com peso 0 são ignorados aqui; eles atuam apenas como regra (ver {@link PoliticaDecisao}).
 */
public class CalculadoraRisco {

    public int calcular(List<SinalRisco> sinais) {
        int pesoTotal = sinais.stream().mapToInt(SinalRisco::peso).sum();
        if (pesoTotal == 0) {
            return 0;
        }
        int somaPonderada = sinais.stream().mapToInt(s -> s.pontuacao() * s.peso()).sum();
        // Arredonda para o inteiro mais próximo sem usar ponto flutuante.
        return (somaPonderada + pesoTotal / 2) / pesoTotal;
    }
}
