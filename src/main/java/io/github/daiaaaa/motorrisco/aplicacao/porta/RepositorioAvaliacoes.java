package io.github.daiaaaa.motorrisco.aplicacao.porta;

import io.github.daiaaaa.motorrisco.aplicacao.AvaliacaoRegistrada;

import java.util.Optional;

/**
 * Porta de saída: onde as avaliações já feitas ficam guardadas, indexadas pelo idTransacao.
 */
public interface RepositorioAvaliacoes {

    Optional<AvaliacaoRegistrada> buscar(String idTransacao);

    /**
     * Grava o registro somente se ainda não existir um para o mesmo idTransacao (operação atômica).
     *
     * @return o registro que vale: o recém-gravado ou o que já estava lá
     */
    AvaliacaoRegistrada salvarSeAusente(AvaliacaoRegistrada registro);
}
