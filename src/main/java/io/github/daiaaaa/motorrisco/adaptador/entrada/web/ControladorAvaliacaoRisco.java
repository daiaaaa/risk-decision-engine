package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.aplicacao.ResultadoAvaliacao;
import io.github.daiaaaa.motorrisco.aplicacao.ServicoAvaliacaoIdempotente;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/avaliacoes")
public class ControladorAvaliacaoRisco {

    /** Indica ao cliente que a resposta foi reaproveitada de uma requisição anterior. */
    static final String CABECALHO_REPETIDA = "X-Resposta-Repetida";

    private final ServicoAvaliacaoIdempotente servico;

    public ControladorAvaliacaoRisco(ServicoAvaliacaoIdempotente servico) {
        this.servico = servico;
    }

    @PostMapping
    public ResponseEntity<RespostaAvaliacao> avaliar(@Valid @RequestBody RequisicaoAvaliacao requisicao) {
        ResultadoAvaliacao resultado = servico.avaliar(requisicao.paraDominio());
        return ResponseEntity.ok()
                .header(CABECALHO_REPETIDA, String.valueOf(resultado.repetida()))
                .body(RespostaAvaliacao.de(resultado.avaliacao()));
    }
}
