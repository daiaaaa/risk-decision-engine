package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.aplicacao.ServicoAvaliacaoRisco;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/avaliacoes")
public class ControladorAvaliacaoRisco {

    private final ServicoAvaliacaoRisco servico;

    public ControladorAvaliacaoRisco(ServicoAvaliacaoRisco servico) {
        this.servico = servico;
    }

    @PostMapping
    public RespostaAvaliacao avaliar(@Valid @RequestBody RequisicaoAvaliacao requisicao) {
        return RespostaAvaliacao.de(servico.avaliar(requisicao.paraDominio()));
    }
}
