package io.github.daiaaaa.motorrisco;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sobe o contexto sem Kafka: o consumidor não inicia e os tópicos não são criados.
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "risco.revisao.motor=memoria"
})
class AplicacaoMotorRiscoTests {

    @Test
    void contextoSobe() {
    }
}
