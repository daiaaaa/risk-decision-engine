package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A fonte de histórico fica mais lenta que o tempo limite, simulando uma queda.
 * A API precisa continuar respondendo rápido, marcar a resposta como degradada
 * e nunca aprovar automaticamente.
 */
@SpringBootTest(properties = {
        "risco.sinais.lista-restritiva.latencia=0ms",
        "risco.sinais.dispositivo.latencia=0ms",
        "risco.sinais.historico.latencia=2s",
        "risco.resiliencia.tempo-limite=100ms"
})
@AutoConfigureMockMvc
class FonteDegradadaApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void fonteLentaNaoDerrubaAAvaliacao() throws Exception {
        // Dispositivo conhecido e valor habitual: com todas as fontes no ar, seria APROVAR.
        mvc.perform(post("/avaliacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idTransacao":"%s","idCliente":"cliente-001","idDispositivo":"dispositivo-abc",
                                 "valor":300.00,"tipo":"PIX"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisao").value("REVISAR"))
                .andExpect(jsonPath("$.degradada").value(true))
                .andExpect(jsonPath("$.sinais[?(@.fonte == 'historico-cliente')].degradado").value(true))
                .andExpect(jsonPath("$.tempoProcessamentoMs").value(lessThan(1000)));
    }
}
