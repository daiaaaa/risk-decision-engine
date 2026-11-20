package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API de revisão manual com a fila em memória. Quem move a decisão REVISAR da avaliação para a
 * fila é o consumidor Kafka (ConsumidorDecisoes), por isso o teste usa um Kafka real
 * (Testcontainers), como o EventosKafkaTest. O fluxo completo com o processo BPMN está em
 * RevisaoManualProcessTest.
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "risco.revisao.motor=memoria"
})
@AutoConfigureMockMvc
@Testcontainers
class RevisoesApiTest {

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.2");

    @Autowired
    private MockMvc mvc;

    @Test
    void analistaDecideENaoApareceMaisNasPendentes() throws Exception {
        String idTransacao = UUID.randomUUID().toString();
        String idTarefa = abrirRevisaoEObterIdTarefa(idTransacao);

        mvc.perform(post("/revisoes/" + idTarefa + "/decisao").with(Tokens.analista())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisao":"NEGAR","justificativa":"dispositivo não reconhecido"}
                                """))
                .andExpect(status().isNoContent());

        mvc.perform(get("/revisoes").with(Tokens.analista()))
                .andExpect(jsonPath("$[?(@.idTarefa == '" + idTarefa + "')]").isEmpty());
    }

    @Test
    void decidirTarefaInexistenteRetorna404() throws Exception {
        mvc.perform(post("/revisoes/" + UUID.randomUUID() + "/decisao").with(Tokens.analista())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisao":"APROVAR"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Revisão não encontrada"));
    }

    @Test
    void decisaoAusenteNaRequisicaoRetorna400() throws Exception {
        String idTarefa = abrirRevisaoEObterIdTarefa(UUID.randomUUID().toString());

        mvc.perform(post("/revisoes/" + idTarefa + "/decisao").with(Tokens.analista())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void avaliadorNaoPodeDecidirRevisao() throws Exception {
        mvc.perform(post("/revisoes/" + UUID.randomUUID() + "/decisao").with(Tokens.avaliador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisao":"APROVAR"}
                                """))
                .andExpect(status().isForbidden());
    }

    /**
     * Abre uma revisão de verdade (dispositivo novo força REVISAR) e devolve o idTarefa gerado.
     * A decisão chega à fila de forma assíncrona (via Kafka), por isso espera até aparecer.
     */
    private String abrirRevisaoEObterIdTarefa(String idTransacao) throws Exception {
        mvc.perform(post("/avaliacoes").with(Tokens.avaliador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idTransacao":"%s","idCliente":"cliente-001","idDispositivo":"dispositivo-novo",
                                 "valor":300.00,"tipo":"PIX"}
                                """.formatted(idTransacao)))
                .andExpect(jsonPath("$.decisao").value("REVISAR"));

        esperar(() -> !idTarefaDe(idTransacao).isEmpty());
        return idTarefaDe(idTransacao).get(0).toString();
    }

    private net.minidev.json.JSONArray idTarefaDe(String idTransacao) {
        try {
            String resposta = mvc.perform(get("/revisoes").with(Tokens.analista()))
                    .andReturn().getResponse().getContentAsString();
            return com.jayway.jsonpath.JsonPath.read(resposta,
                    "$[?(@.idTransacao == '" + idTransacao + "')].idTarefa");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void esperar(BooleanSupplier condicao) {
        long limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condicao.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                fail("condição não atendida em 30 s");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
