package io.github.daiaaaa.motorrisco.aplicacao;

/**
 * O idTransacao já foi usado em uma avaliação com dados diferentes. Devolver a decisão
 * guardada seria responder sobre outra transação, então a requisição é rejeitada.
 */
public class ConflitoIdempotenciaException extends RuntimeException {

    private final String idTransacao;

    public ConflitoIdempotenciaException(String idTransacao) {
        super("o idTransacao " + idTransacao + " já foi usado com dados diferentes");
        this.idTransacao = idTransacao;
    }

    public String idTransacao() {
        return idTransacao;
    }
}
