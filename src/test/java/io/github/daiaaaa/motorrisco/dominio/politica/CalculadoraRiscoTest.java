package io.github.daiaaaa.motorrisco.dominio.politica;

import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraRiscoTest {

    private final CalculadoraRisco calculadora = new CalculadoraRisco();

    @Test
    void calculaMediaPonderada() {
        List<SinalRisco> sinais = List.of(
                SinalRisco.pontuado("a", 80, 75, "x"),
                SinalRisco.pontuado("b", 20, 25, "y"));

        // (80*75 + 20*25) / 100 = 65
        assertThat(calculadora.calcular(sinais)).isEqualTo(65);
    }

    @Test
    void ignoraSinaisComPesoZero() {
        List<SinalRisco> sinais = List.of(
                SinalRisco.pontuado("regra", 0, 0, "só regra"),
                SinalRisco.pontuado("dispositivo", 70, 50, "dispositivo desconhecido"));

        assertThat(calculadora.calcular(sinais)).isEqualTo(70);
    }

    @Test
    void arredondaParaOInteiroMaisProximo() {
        List<SinalRisco> sinais = List.of(
                SinalRisco.pontuado("a", 70, 50, "x"),
                SinalRisco.pontuado("b", 85, 50, "y"));

        // 77,5 arredonda para 78
        assertThat(calculadora.calcular(sinais)).isEqualTo(78);
    }

    @Test
    void retornaZeroQuandoNenhumSinalTemPeso() {
        assertThat(calculadora.calcular(List.of(SinalRisco.bloqueio("lista-restritiva", "bloqueado")))).isZero();
    }
}
