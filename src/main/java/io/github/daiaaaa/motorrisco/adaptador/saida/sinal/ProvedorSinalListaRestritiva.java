package io.github.daiaaaa.motorrisco.adaptador.saida.sinal;

import io.github.daiaaaa.motorrisco.aplicacao.porta.ProvedorSinal;
import io.github.daiaaaa.motorrisco.dominio.modelo.SinalRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Regra rígida: clientes na lista restritiva são sempre recusados.
 * O peso é 0 porque esta fonte não entra na pontuação; ela bloqueia.
 */
@Component
public class ProvedorSinalListaRestritiva implements ProvedorSinal {

    static final String NOME = "lista-restritiva";

    private static final Set<String> CLIENTES_BLOQUEADOS = Set.of("cliente-bloqueado");

    private final Duration latencia;

    public ProvedorSinalListaRestritiva(
            @Value("${risco.sinais.lista-restritiva.latencia:20ms}") Duration latencia) {
        this.latencia = latencia;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public SinalRisco avaliar(Transacao transacao) {
        LatenciaSimulada.aguardar(latencia);
        if (CLIENTES_BLOQUEADOS.contains(transacao.idCliente())) {
            return SinalRisco.bloqueio(NOME, "cliente está na lista restritiva");
        }
        return SinalRisco.pontuado(NOME, 0, 0, "cliente não está na lista restritiva");
    }
}
