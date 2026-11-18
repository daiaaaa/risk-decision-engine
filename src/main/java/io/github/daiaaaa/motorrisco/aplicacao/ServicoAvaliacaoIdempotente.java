package io.github.daiaaaa.motorrisco.aplicacao;

import io.github.daiaaaa.motorrisco.aplicacao.porta.PublicadorDecisoes;
import io.github.daiaaaa.motorrisco.aplicacao.porta.RegistroMetricas;
import io.github.daiaaaa.motorrisco.aplicacao.porta.RepositorioAvaliacoes;
import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;

import java.util.Optional;

/**
 * Garante que o mesmo idTransacao sempre recebe a mesma decisão.
 *
 * <ol>
 *   <li>Já existe avaliação para o idTransacao: devolve a guardada, sem avaliar de novo.</li>
 *   <li>Não existe: avalia, grava com "salvar se ausente" e, se a gravação foi desta
 *       requisição, publica o evento da decisão.</li>
 *   <li>Se outra requisição gravou primeiro (corrida), devolve a dela, para que as duas
 *       respostas sejam iguais.</li>
 * </ol>
 *
 * Em qualquer caso de reaproveitamento, os dados precisam ser os mesmos da requisição
 * original; senão, {@link ConflitoIdempotenciaException}.
 *
 * <p>Na corrida, as duas requisições avaliam, mas só uma grava. Isso é aceitável porque a
 * avaliação não tem efeito colateral: ela só lê sinais. Evita uma trava distribuída.
 */
public class ServicoAvaliacaoIdempotente {

    private final ServicoAvaliacaoRisco avaliador;
    private final RepositorioAvaliacoes repositorio;
    private final PublicadorDecisoes publicador;
    private final RegistroMetricas metricas;

    public ServicoAvaliacaoIdempotente(ServicoAvaliacaoRisco avaliador,
                                       RepositorioAvaliacoes repositorio,
                                       PublicadorDecisoes publicador,
                                       RegistroMetricas metricas) {
        this.avaliador = avaliador;
        this.repositorio = repositorio;
        this.publicador = publicador;
        this.metricas = metricas;
    }

    public ResultadoAvaliacao avaliar(Transacao transacao) {
        String impressao = ImpressaoRequisicao.de(transacao);

        Optional<AvaliacaoRegistrada> existente = repositorio.buscar(transacao.idTransacao());
        if (existente.isPresent()) {
            return reaproveitar(existente.get(), impressao, transacao.idTransacao());
        }

        AvaliacaoRisco nova = avaliador.avaliar(transacao);
        AvaliacaoRegistrada vencedora = repositorio.salvarSeAusente(new AvaliacaoRegistrada(impressao, nova));

        if (vencedora.avaliacao().idAvaliacao().equals(nova.idAvaliacao())) {
            // Só quem gravou publica e conta: um reenvio ou a requisição que perdeu a corrida não.
            publicador.publicar(transacao, nova);
            metricas.decisaoEmitida(nova);
            return new ResultadoAvaliacao(nova, false);
        }
        return reaproveitar(vencedora, impressao, transacao.idTransacao());
    }

    private ResultadoAvaliacao reaproveitar(AvaliacaoRegistrada registro, String impressao, String idTransacao) {
        if (!registro.impressaoRequisicao().equals(impressao)) {
            metricas.conflitoIdempotencia();
            throw new ConflitoIdempotenciaException(idTransacao);
        }
        metricas.respostaRepetida();
        return new ResultadoAvaliacao(registro.avaliacao(), true);
    }
}
