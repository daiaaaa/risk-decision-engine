package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.aplicacao.ResultadoAvaliacao;
import io.github.daiaaaa.motorrisco.aplicacao.ServicoAvaliacaoIdempotente;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/avaliacoes")
@Tag(name = "Avaliações", description = "Decisão de risco em tempo real")
public class ControladorAvaliacaoRisco {

    /** Indica ao cliente que a resposta foi reaproveitada de uma requisição anterior. */
    static final String CABECALHO_REPETIDA = "X-Resposta-Repetida";

    private final ServicoAvaliacaoIdempotente servico;

    public ControladorAvaliacaoRisco(ServicoAvaliacaoIdempotente servico) {
        this.servico = servico;
    }

    @PostMapping
    @Operation(
            summary = "Avalia o risco de uma transação",
            description = """
                    Consulta as fontes de sinais em paralelo e devolve APROVAR, REVISAR ou NEGAR.

                    O `idTransacao` é a chave de idempotência: reenviar a mesma transação devolve a
                    mesma avaliação (cabeçalho `X-Resposta-Repetida: true`), sem avaliar de novo.

                    Exige o papel `avaliador`.""")
    @ApiResponse(responseCode = "200", description = "Avaliação feita (ou reaproveitada, em caso de reenvio)",
            headers = @Header(name = CABECALHO_REPETIDA, description = "true quando a resposta veio de uma requisição anterior",
                    schema = @Schema(type = "boolean")))
    @ApiResponse(responseCode = "400", description = "Requisição inválida",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido", content = @Content)
    @ApiResponse(responseCode = "403", description = "Token sem o papel avaliador", content = @Content)
    @ApiResponse(responseCode = "409", description = "idTransacao já usado com dados diferentes",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<RespostaAvaliacao> avaliar(@Valid @RequestBody RequisicaoAvaliacao requisicao) {
        ResultadoAvaliacao resultado = servico.avaliar(requisicao.paraDominio());
        return ResponseEntity.ok()
                .header(CABECALHO_REPETIDA, String.valueOf(resultado.repetida()))
                .body(RespostaAvaliacao.de(resultado.avaliacao()));
    }
}
