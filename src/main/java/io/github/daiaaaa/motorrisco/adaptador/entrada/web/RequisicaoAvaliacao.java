package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.dominio.modelo.TipoTransacao;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
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
public record RequisicaoAvaliacao(
        @NotBlank @Size(max = 64) String idTransacao,
        @NotBlank @Size(max = 64) String idCliente,
        @NotBlank @Size(max = 128) String idDispositivo,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal valor,
        @NotNull TipoTransacao tipo) {

    Transacao paraDominio() {
        return new Transacao(idTransacao, idCliente, idDispositivo, valor, tipo);
    }
}
