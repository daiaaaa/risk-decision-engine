package io.github.daiaaaa.motorrisco.adaptador.entrada.kafka;

/**
 * A mensagem não pode ser processada e tentar de novo não vai resolver (JSON inválido,
 * campo obrigatório ausente, versão desconhecida). Vai direto para a DLQ, sem novas tentativas.
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    public EventoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
