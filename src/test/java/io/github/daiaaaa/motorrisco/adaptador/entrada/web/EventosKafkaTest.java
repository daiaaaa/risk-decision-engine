package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.adaptador.saida.kafka.TopicosKafka;
import io.github.daiaaaa.motorrisco.aplicacao.ServicoRevisaoManual;
import io.github.daiaaaa.motorrisco.dominio.modelo.TarefaRevisao;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eventos de ponta a ponta com um Kafka real em Docker: a API publica, o tópico entrega,
 * o consumidor encaminha para a revisão, e mensagens inválidas vão para a DLQ.
 */
@SpringBootTest(properties = {
        "risco.sinais.lista-restritiva.latencia=0ms",
        "risco.sinais.dispositivo.latencia=0ms",
        "risco.sinais.historico.latencia=0ms",
        "spring.kafka.listener.auto-startup=true",
        "risco.revisao.motor=memoria"
})
@AutoConfigureMockMvc
@Testcontainers
class EventosKafkaTest {

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.2");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ServicoRevisaoManual revisoes;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ConsumerFactory<String, String> fabricaConsumidores;

    @Test
    void decisaoRevisarChegaNaFilaDeRevisaoEAprovarNao() throws Exception {
        String idAprovada = UUID.randomUUID().toString();
        String idRevisar = UUID.randomUUID().toString();

        // Mesmo cliente, então a mesma partição: a APROVAR é consumida antes da REVISAR.
        avaliar(idAprovada, "dispositivo-abc").andExpect(jsonPath("$.decisao").value("APROVAR"));
        avaliar(idRevisar, "dispositivo-novo").andExpect(jsonPath("$.decisao").value("REVISAR"));

        aguardar(() -> idsEmRevisao().contains(idRevisar));

        assertThat(idsEmRevisao()).doesNotContain(idAprovada);
        mvc.perform(get("/revisoes").with(Tokens.analista()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idTransacao", hasItem(idRevisar)));
    }

    @Test
    void mensagemInvalidaVaiParaADlq() throws Exception {
        String lixo = "mensagem inválida " + UUID.randomUUID();
        kafka.send(TopicosKafka.DECISOES, "cliente-qualquer", lixo).get();

        try (Consumer<String, String> consumidor = fabricaConsumidores.createConsumer("teste-dlq-" + UUID.randomUUID(), null)) {
            consumidor.subscribe(List.of(TopicosKafka.DECISOES_DLQ));
            ConsumerRecord<String, String> naDlq =
                    KafkaTestUtils.getSingleRecord(consumidor, TopicosKafka.DECISOES_DLQ, Duration.ofSeconds(30));

            assertThat(naDlq.value()).isEqualTo(lixo);
            // O Spring Kafka anexa nos cabeçalhos o motivo da falha.
            assertThat(cabecalhos(naDlq)).contains("EventoInvalidoException");
        }
    }

    private ResultActions avaliar(String idTransacao, String idDispositivo) throws Exception {
        return mvc.perform(post("/avaliacoes").with(Tokens.avaliador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idTransacao":"%s","idCliente":"cliente-001","idDispositivo":"%s",
                                 "valor":300.00,"tipo":"PIX"}
                                """.formatted(idTransacao, idDispositivo)))
                .andExpect(status().isOk());
    }

    private List<String> idsEmRevisao() {
        return revisoes.pendentes().stream().map(t -> t.revisao().idTransacao()).toList();
    }

    private static String cabecalhos(ConsumerRecord<String, String> registro) {
        StringBuilder texto = new StringBuilder();
        for (Header cabecalho : registro.headers()) {
            texto.append(cabecalho.key()).append('=')
                    .append(new String(cabecalho.value(), StandardCharsets.UTF_8)).append('\n');
        }
        return texto.toString();
    }

    private static void aguardar(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condicao.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                fail("a condição não foi atendida em 30 s");
            }
            Thread.sleep(200);
        }
    }
}
