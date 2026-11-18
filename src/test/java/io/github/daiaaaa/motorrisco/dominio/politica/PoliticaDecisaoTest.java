package io.github.daiaaaa.motorrisco.dominio.politica;

import io.github.daiaaaa.motorrisco.dominio.modelo.Decisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PoliticaDecisaoTest {

    private final PoliticaDecisao politica = new PoliticaDecisao(40, 75);

    @ParameterizedTest(name = "pontuação {0} -> {1}")
    @CsvSource({
            "0,   APROVAR",
            "39,  APROVAR",
            "40,  REVISAR",
            "74,  REVISAR",
            "75,  NEGAR",
            "100, NEGAR"
    })
    void aplicaOsLimiares(int pontuacao, Decisao esperada) {
        assertThat(politica.decidir(pontuacao, List.of())).isEqualTo(esperada);
    }

    @Test
    void sinalBloqueanteNegaMesmoComPontuacaoBaixa() {
        List<SinalRisco> sinais = List.of(SinalRisco.bloqueio("lista-restritiva", "bloqueado"));

        assertThat(politica.decidir(0, sinais)).isEqualTo(Decisao.NEGAR);
    }

    @Test
    void sinalDegradadoTransformaAprovarEmRevisar() {
        List<SinalRisco> sinais = List.of(
                SinalRisco.pontuado("dispositivo", 5, 50, "dispositivo conhecido"),
                SinalRisco.indisponivel("historico-cliente", "tempo limite"));

        assertThat(politica.decidir(5, sinais)).isEqualTo(Decisao.REVISAR);
    }

    @Test
    void sinalDegradadoNaoSuavizaNegar() {
        List<SinalRisco> sinais = List.of(
                SinalRisco.pontuado("dispositivo", 90, 50, "dispositivo suspeito"),
                SinalRisco.indisponivel("historico-cliente", "tempo limite"));

        assertThat(politica.decidir(90, sinais)).isEqualTo(Decisao.NEGAR);
    }

    @Test
    void rejeitaLimiaresInvalidos() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PoliticaDecisao(80, 40));
        assertThatIllegalArgumentException().isThrownBy(() -> new PoliticaDecisao(-1, 40));
        assertThatIllegalArgumentException().isThrownBy(() -> new PoliticaDecisao(40, 101));
    }
}
