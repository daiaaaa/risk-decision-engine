package io.github.daiaaaa.motorrisco.aplicacao;

import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Impressão digital (SHA-256) dos dados de uma transação. Serve para detectar quando o mesmo
 * idTransacao é reenviado com dados diferentes.
 */
final class ImpressaoRequisicao {

    private ImpressaoRequisicao() {
    }

    static String de(Transacao transacao) {
        // stripTrailingZeros: 300, 300.0 e 300.00 representam o mesmo valor.
        String canonica = String.join("|",
                transacao.idCliente(),
                transacao.idDispositivo(),
                transacao.valor().stripTrailingZeros().toPlainString(),
                transacao.tipo().name());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonica.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
