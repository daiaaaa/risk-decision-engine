package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * As métricas e os health checks funcionam com a aplicação inteira de pé. Roda sem Redis e sem
 * Kafka, o que também prova que a readiness continua UP sem eles.
 */
@SpringBootTest(properties = {
        "risco.sinais.lista-restritiva.latencia=0ms",
        "risco.sinais.dispositivo.latencia=0ms",
        "risco.sinais.historico.latencia=0ms",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
@AutoConfigureMockMvc
class ObservabilidadeApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MeterRegistry registro;

    @Test
    void avaliacaoGeraMetricasDeDecisaoEDeFonte() throws Exception {
        double antes = decisoesAprovadas();

        mvc.perform(post("/avaliacoes").with(Tokens.avaliador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idTransacao":"%s","idCliente":"cliente-001","idDispositivo":"dispositivo-abc",
                                 "valor":300.00,"tipo":"PIX"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());

        assertThat(decisoesAprovadas()).isEqualTo(antes + 1);
        for (String fonte : new String[] {"dispositivo", "historico-cliente", "lista-restritiva"}) {
            assertThat(registro.get("risco.fonte.duracao").tags("fonte", fonte, "resultado", "ok").timer().count())
                    .as("medição da fonte %s", fonte)
                    .isPositive();
        }
    }

    @Test
    void estadoDosCircuitBreakersViraMetrica() {
        for (String fonte : new String[] {"dispositivo", "historico-cliente", "lista-restritiva"}) {
            double fechado = registro.get("resilience4j.circuitbreaker.state")
                    .tags("name", fonte, "state", "closed").gauge().value();
            assertThat(fechado).as("circuito da fonte %s fechado", fonte).isEqualTo(1.0);
        }
    }

    @Test
    void readinessContinuaUpSemRedisESemKafka() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private double decisoesAprovadas() {
        var contador = registro.find("risco.decisoes").tags("decisao", "APROVAR", "degradada", "false").counter();
        return contador == null ? 0 : contador.count();
    }
}
