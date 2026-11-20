package io.github.daiaaaa.motorrisco.aplicacao;

/**
 * A tarefa de revisão não existe ou já foi concluída (por outro analista ou pelo prazo).
 */
public class RevisaoNaoEncontradaException extends RuntimeException {

    private final String idTarefa;

    public RevisaoNaoEncontradaException(String idTarefa) {
        super("a tarefa de revisão " + idTarefa + " não existe ou já foi concluída");
        this.idTarefa = idTarefa;
    }

    public String idTarefa() {
        return idTarefa;
    }
}
