package io.github.daiaaaa.motorrisco.aplicacao;

import io.github.daiaaaa.motorrisco.aplicacao.porta.RepositorioAvaliacoes;
import io.github.daiaaaa.motorrisco.dominio.modelo.AvaliacaoRisco;
import io.github.daiaaaa.motorrisco.dominio.modelo.Transacao;

import java.util.Optional;

/**
 * Garante que o mesmo idTransacao sempre recebe a mesma decisão.
 *
 * <ol>
 *   <li>Já existe avaliação para o idTransacao: devolve a guardada, sem avaliar de novo.</li>
 *   <li>Não existe: avalia e grava com "salvar se ausente".</li>
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

    public ServicoAvaliacaoIdempotente(ServicoAvaliacaoRisco avaliador, RepositorioAvaliacoes repositorio) {
        this.avaliador = avaliador;
        this.repositorio = repositorio;
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
            return new ResultadoAvaliacao(nova, false);
        }
        return reaproveitar(vencedora, impressao, transacao.idTransacao());
    }

    private static ResultadoAvaliacao reaproveitar(AvaliacaoRegistrada registro, String impressao,
                                                   String idTransacao) {
        if (!registro.impressaoRequisicao().equals(impressao)) {
            throw new ConflitoIdempotenciaException(idTransacao);
        }
        return new ResultadoAvaliacao(registro.avaliacao(), true);
    }
}
