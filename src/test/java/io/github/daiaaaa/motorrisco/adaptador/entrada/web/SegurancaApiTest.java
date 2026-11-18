package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem pode chamar o quê. 401 = sem token (ou token inválido); 403 = token válido sem o papel.
 */
@SpringBootTest(properties = {
        "risco.sinais.lista-restritiva.latencia=0ms",
        "risco.sinais.dispositivo.latencia=0ms",
        "risco.sinais.historico.latencia=0ms",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
@AutoConfigureMockMvc
class SegurancaApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(avaliacao())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    @Test
    void avaliadorPodeAvaliar() throws Exception {
        mvc.perform(avaliacao().with(Tokens.avaliador())).andExpect(status().isOk());
    }

    @Test
    void analistaNaoPodeAvaliar() throws Exception {
        mvc.perform(avaliacao().with(Tokens.analista())).andExpect(status().isForbidden());
    }

    @Test
    void soAnalistaVeAsRevisoes() throws Exception {
        mvc.perform(get("/revisoes").with(Tokens.analista())).andExpect(status().isOk());
        mvc.perform(get("/revisoes").with(Tokens.avaliador())).andExpect(status().isForbidden());
        mvc.perform(get("/revisoes")).andExpect(status().isUnauthorized());
    }

    @Test
    void metricasExigemOPapelMonitoramento() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").with(Tokens.avaliador())).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").with(Tokens.monitoramento())).andExpect(status().isOk());
    }

    @Test
    void healthFicaAbertoMasSemDetalhesParaAnonimos() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health").with(Tokens.monitoramento()))
                .andExpect(jsonPath("$.components").exists());
    }

    @Test
    void endpointNaoMapeadoNasceFechado() throws Exception {
        mvc.perform(get("/avaliacoes").with(Tokens.avaliador())).andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder avaliacao() {
        return post("/avaliacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idTransacao":"%s","idCliente":"cliente-001","idDispositivo":"dispositivo-abc",
                         "valor":300.00,"tipo":"PIX"}
                        """.formatted(UUID.randomUUID()));
    }
}
