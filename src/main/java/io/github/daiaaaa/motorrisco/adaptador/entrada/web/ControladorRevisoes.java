package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.aplicacao.ServicoRevisaoManual;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;
import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Lista as transações que aguardam um analista. Na fase 6, a revisão passa a ser um processo
 * no Camunda, com tarefas para os analistas.
 */
@RestController
@RequestMapping("/revisoes")
public class ControladorRevisoes {

    private final ServicoRevisaoManual servico;

    public ControladorRevisoes(ServicoRevisaoManual servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<RespostaRevisao> pendentes() {
        return servico.pendentes().stream().map(RespostaRevisao::de).toList();
    }

    public record RespostaRevisao(String idAvaliacao, String idTransacao, String idCliente, BigDecimal valor,
                                  TipoTransacao tipo, int pontuacao, boolean degradada, Instant avaliadoEm) {

        static RespostaRevisao de(RevisaoPendente r) {
            return new RespostaRevisao(r.idAvaliacao(), r.idTransacao(), r.idCliente(), r.valor(), r.tipo(),
                    r.pontuacao(), r.degradada(), r.avaliadoEm());
        }
    }
}
