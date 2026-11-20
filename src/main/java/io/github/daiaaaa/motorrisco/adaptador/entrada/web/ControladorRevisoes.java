package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.aplicacao.ServicoRevisaoManual;
import io.github.daiaaaa.motorrisco.dominio.modelo.DecisaoAnalista;
import io.github.daiaaaa.motorrisco.dominio.modelo.RevisaoPendente;
import io.github.daiaaaa.motorrisco.dominio.modelo.TarefaRevisao;
import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Transações que aguardam um analista de fraude. Com o Camunda, cada revisão é uma user task
 * do processo BPMN "revisao-manual", com prazo.
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

    @PostMapping("/{idTarefa}/decisao")
    @Operation(
            summary = "Registra a decisão do analista",
            description = """
                    Aprova ou nega a transação e encerra a revisão. O analista é identificado pelo token,
                    não pelo corpo da requisição. Exige o papel `analista`.""")
    @ApiResponse(responseCode = "204", description = "Decisão registrada")
    @ApiResponse(responseCode = "400", description = "Requisição inválida",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido", content = @Content)
    @ApiResponse(responseCode = "403", description = "Token sem o papel analista", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tarefa inexistente ou já concluída (por outro analista ou pelo prazo)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> decidir(@PathVariable String idTarefa,
                                        @Valid @RequestBody RequisicaoDecisao requisicao,
                                        @Parameter(hidden = true) Authentication autenticacao) {
        servico.decidir(idTarefa, requisicao.decisao(), autenticacao.getName(), requisicao.justificativa());
        return ResponseEntity.noContent().build();
    }

    @Schema(description = "Decisão do analista sobre uma transação em revisão")
    public record RequisicaoDecisao(
            @Schema(description = "APROVAR libera a transação; NEGAR bloqueia", example = "NEGAR")
            @NotNull DecisaoAnalista decisao,

            @Schema(description = "Motivo da decisão, para auditoria", example = "Dispositivo novo e cliente não reconhece a compra")
            @Size(max = 500) String justificativa) {
    }

    @Schema(description = "Transação aguardando um analista")
    public record RespostaRevisao(
            @Schema(description = "Identificador da tarefa; use em POST /revisoes/{idTarefa}/decisao")
            String idTarefa,
            String idAvaliacao,
            String idTransacao,
            @Schema(example = "cliente-001") String idCliente,
            @Schema(example = "300.00") BigDecimal valor,
            TipoTransacao tipo,
            @Schema(example = "40") int pontuacao,
            @Schema(description = "Verdadeiro quando a revisão foi causada por uma fonte de sinais fora do ar")
            boolean degradada,
            Instant avaliadoEm) {

        static RespostaRevisao de(TarefaRevisao tarefa) {
            RevisaoPendente r = tarefa.revisao();
            return new RespostaRevisao(tarefa.idTarefa(), r.idAvaliacao(), r.idTransacao(), r.idCliente(), r.valor(),
                    r.tipo(), r.pontuacao(), r.degradada(), r.avaliadoEm());
        }
    }
}
