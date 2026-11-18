package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O contrato OpenAPI gerado descreve a API como ela é: endpoints, campos obrigatórios,
 * respostas de erro e o esquema de segurança do Keycloak.
 */
class DocumentacaoApiTest {

    @Nested
    @SpringBootTest(properties = {
            "spring.kafka.listener.auto-startup=false",
            "spring.kafka.admin.auto-create=false"
    })
    @AutoConfigureMockMvc
    class AmbienteLocal {

        @Autowired
        private MockMvc mvc;

        @Test
        void contratoDescreveOsEndpointsDaApi() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("Motor de Decisão de Risco"))
                    .andExpect(jsonPath("$.paths['/avaliacoes'].post.summary").value("Avalia o risco de uma transação"))
                    .andExpect(jsonPath("$.paths['/avaliacoes'].post.responses['409']").exists())
                    .andExpect(jsonPath("$.paths['/revisoes'].get").exists())
                    // O Actuator não faz parte do contrato público.
                    .andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
        }

        @Test
        void validacoesViramCamposObrigatorios() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.components.schemas.RequisicaoAvaliacao.required",
                            hasItems("idTransacao", "idCliente", "idDispositivo", "valor", "tipo")));
        }

        @Test
        void esquemaDeSegurancaApontaParaOKeycloak() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.components.securitySchemes.keycloak.type").value("oauth2"))
                    .andExpect(jsonPath("$.components.securitySchemes.keycloak.flows.clientCredentials.tokenUrl",
                            endsWith("/realms/motor-risco/protocol/openid-connect/token")))
                    .andExpect(jsonPath("$.components.securitySchemes.keycloak.flows.authorizationCode.authorizationUrl",
                            endsWith("/realms/motor-risco/protocol/openid-connect/auth")))
                    .andExpect(jsonPath("$.security[0].keycloak").exists());
        }

        @Test
        void swaggerUiAbreSemToken() throws Exception {
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.kafka.listener.auto-startup=false",
            "spring.kafka.admin.auto-create=false"
    })
    @AutoConfigureMockMvc
    @ActiveProfiles("producao")
    class PerfilProducao {

        @Autowired
        private MockMvc mvc;

        @Test
        void documentacaoFicaDesligada() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        }
    }
}
