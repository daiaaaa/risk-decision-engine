package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.aplicacao.ConflitoIdempotenciaException;
import io.github.daiaaaa.motorrisco.aplicacao.RevisaoNaoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz exceções da aplicação em respostas HTTP no formato Problem Details (RFC 9457).
 */
@RestControllerAdvice
public class TratadorErros {

    @ExceptionHandler(ConflitoIdempotenciaException.class)
    public ProblemDetail conflitoIdempotencia(ConflitoIdempotenciaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "O idTransacao " + e.idTransacao() + " já foi avaliado com dados diferentes. "
                        + "Use um novo idTransacao para uma nova transação.");
        problema.setTitle("Conflito de idempotência");
        problema.setProperty("idTransacao", e.idTransacao());
        return problema;
    }

    @ExceptionHandler(RevisaoNaoEncontradaException.class)
    public ProblemDetail revisaoNaoEncontrada(RevisaoNaoEncontradaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "A tarefa de revisão " + e.idTarefa() + " não existe ou já foi concluída "
                        + "(por outro analista ou porque o prazo acabou).");
        problema.setTitle("Revisão não encontrada");
        problema.setProperty("idTarefa", e.idTarefa());
        return problema;
    }
}
