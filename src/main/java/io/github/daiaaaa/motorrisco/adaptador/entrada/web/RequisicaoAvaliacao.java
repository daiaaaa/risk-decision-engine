package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Contrato de entrada da API. A validação acontece aqui, na fronteira de confiança,
 * para que o domínio só receba dados bem formados.
 */
@Schema(description = "Transação a ser avaliada")
public record RequisicaoAvaliacao(
        @Schema(description = "Identificador único da transação; também é a chave de idempotência", example = "tx-2026-0001")
        @NotBlank @Size(max = 64) String idTransacao,

        @Schema(description = "Cliente que faz a transação", example = "cliente-001")
        @NotBlank @Size(max = 64) String idCliente,

        @Schema(description = "Impressão digital do dispositivo de origem", example = "dispositivo-abc")
        @NotBlank @Size(max = 128) String idDispositivo,

        @Schema(description = "Valor em reais, com até 2 casas decimais", example = "300.00")
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal valor,

        @Schema(description = "Tipo da transação", example = "PIX")
        @NotNull TipoTransacao tipo) {

    Transacao paraDominio() {
        return new Transacao(idTransacao, idCliente, idDispositivo, valor, tipo);
    }
}
