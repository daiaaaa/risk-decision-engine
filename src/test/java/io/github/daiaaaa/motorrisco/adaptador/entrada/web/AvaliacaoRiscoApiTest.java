package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de ponta a ponta via HTTP, com os provedores reais e latência simulada zerada.
 */
@SpringBootTest(properties = {
        "risco.sinais.lista-restritiva.latencia=0ms",
        "risco.sinais.dispositivo.latencia=0ms",
        "risco.sinais.historico.latencia=0ms"
})
@AutoConfigureMockMvc
class AvaliacaoRiscoApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void dispositivoConhecidoEValorHabitualEAprovado() throws Exception {
        avaliar("cliente-001", "dispositivo-abc", "300.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisao").value("APROVAR"))
                .andExpect(jsonPath("$.pontuacao").value(8))
                .andExpect(jsonPath("$.degradada").value(false))
                .andExpect(jsonPath("$.sinais", hasSize(3)));
    }

    @Test
    void dispositivoNovoVaiParaRevisao() throws Exception {
        avaliar("cliente-001", "dispositivo-novo", "300.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisao").value("REVISAR"))
                .andExpect(jsonPath("$.pontuacao").value(40));
    }

    @Test
    void dispositivoNovoEValorForaDoPadraoENegado() throws Exception {
        avaliar("cliente-001", "dispositivo-novo", "3000.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisao").value("NEGAR"))
                .andExpect(jsonPath("$.pontuacao").value(78));
    }

    @Test
    void clienteNaListaRestritivaENegadoIndependenteDaPontuacao() throws Exception {
        avaliar("cliente-bloqueado", "dispositivo-abc", "10.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisao").value("NEGAR"))
                .andExpect(jsonPath("$.sinais[?(@.fonte == 'lista-restritiva')].bloqueante").value(true));
    }

    @Test
    void requisicaoInvalidaRetornaProblemDetailEmPortugues() throws Exception {
        mvc.perform(post("/avaliacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idTransacao":"","idCliente":"cliente-001",
                                 "idDispositivo":"dispositivo-abc","valor":-5,"tipo":"PIX"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição inválida"));
    }

    /** Cada chamada usa um idTransacao novo, para que um teste não reaproveite a avaliação de outro. */
    private ResultActions avaliar(String idCliente, String idDispositivo, String valor) throws Exception {
        return mvc.perform(post("/avaliacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idTransacao":"%s","idCliente":"%s","idDispositivo":"%s",
                         "valor":%s,"tipo":"PIX"}
                        """.formatted(UUID.randomUUID(), idCliente, idDispositivo, valor)));
    }
}
