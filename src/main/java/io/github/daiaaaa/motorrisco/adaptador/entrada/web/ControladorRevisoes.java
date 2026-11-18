package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.aplicacao.ServicoRevisaoManual;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;
import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Revisões", description = "Transações que aguardam um analista de fraude")
public class ControladorRevisoes {

    private final ServicoRevisaoManual servico;

    public ControladorRevisoes(ServicoRevisaoManual servico) {
        this.servico = servico;
    }

    @GetMapping
    @Operation(
            summary = "Lista as revisões pendentes",
            description = "Transações que receberam a decisão REVISAR, da mais antiga para a mais nova. "
                    + "Exige o papel `analista`.")
    @ApiResponse(responseCode = "200", description = "Revisões pendentes")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido", content = @Content)
    @ApiResponse(responseCode = "403", description = "Token sem o papel analista", content = @Content)
    public List<RespostaRevisao> pendentes() {
        return servico.pendentes().stream().map(RespostaRevisao::de).toList();
    }

    @Schema(description = "Transação aguardando um analista")
    public record RespostaRevisao(
            String idAvaliacao,
            String idTransacao,
            @Schema(example = "cliente-001") String idCliente,
            @Schema(example = "300.00") BigDecimal valor,
            TipoTransacao tipo,
            @Schema(example = "40") int pontuacao,
            @Schema(description = "Verdadeiro quando a revisão foi causada por uma fonte de sinais fora do ar")
            boolean degradada,
            Instant avaliadoEm) {

        static RespostaRevisao de(RevisaoPendente r) {
            return new RespostaRevisao(r.idAvaliacao(), r.idTransacao(), r.idCliente(), r.valor(), r.tipo(),
                    r.pontuacao(), r.degradada(), r.avaliadoEm());
        }
    }
}
