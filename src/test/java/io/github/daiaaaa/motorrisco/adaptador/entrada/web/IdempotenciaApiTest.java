package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Idempotência de ponta a ponta: API, serviço e um Redis real em Docker.
 * O {@code @ServiceConnection} faz o Spring se conectar ao Redis do contêiner.
 */
@SpringBootTest(properties = {
        "risco.sinais.lista-restritiva.latencia=0ms",
        "risco.sinais.dispositivo.latencia=0ms",
        "risco.sinais.historico.latencia=0ms",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
@AutoConfigureMockMvc
@Testcontainers
class IdempotenciaApiTest {

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mvc;

    @Test
    void reenvioDevolveAMesmaAvaliacao() throws Exception {
        String idTransacao = UUID.randomUUID().toString();

        String primeira = avaliar(idTransacao, "300.00")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Resposta-Repetida", "false"))
                .andReturn().getResponse().getContentAsString();
        String idAvaliacao = JsonPath.read(primeira, "$.idAvaliacao");

        avaliar(idTransacao, "300.00")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Resposta-Repetida", "true"))
                .andExpect(jsonPath("$.idAvaliacao").value(idAvaliacao))
                .andExpect(jsonPath("$.decisao").value("APROVAR"));
    }

    @Test
    void mesmoIdComOutroValorRetorna409() throws Exception {
        String idTransacao = UUID.randomUUID().toString();
        avaliar(idTransacao, "300.00").andExpect(status().isOk());

        avaliar(idTransacao, "9999.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito de idempotência"))
                .andExpect(jsonPath("$.idTransacao").value(idTransacao));
    }

    private ResultActions avaliar(String idTransacao, String valor) throws Exception {
        return mvc.perform(post("/avaliacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idTransacao":"%s","idCliente":"cliente-001","idDispositivo":"dispositivo-abc",
                         "valor":%s,"tipo":"PIX"}
                        """.formatted(idTransacao, valor)));
    }
}
