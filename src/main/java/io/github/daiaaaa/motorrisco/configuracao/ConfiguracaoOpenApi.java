package io.github.daiaaaa.motorrisco.configuracao;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrato OpenAPI 3 gerado a partir do código (code-first), servido em /v3/api-docs e
 * navegável em /swagger-ui.html.
 *
 * <p>O esquema de segurança aponta para o Keycloak, então o botão "Authorize" da Swagger UI
 * pede um token de verdade:
 * <ul>
 *   <li><b>client_credentials</b>: para sistemas (cliente sistema-pagamentos).</li>
 *   <li><b>authorization_code com PKCE</b>: para pessoas (cliente painel-analistas).</li>
 * </ul>
 */
@Configuration
public class ConfiguracaoOpenApi {

    static final String ESQUEMA_SEGURANCA = "keycloak";

    @Bean
    public OpenAPI contratoApi(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String emissor) {
        String oidc = emissor + "/protocol/openid-connect";
        return new OpenAPI()
                .info(new Info()
                        .title("Motor de Decisão de Risco")
                        .version("1.0")
                        .description("""
                                Avalia transações financeiras em tempo real e devolve APROVAR, REVISAR ou NEGAR.

                                **Autenticação:** clique em *Authorize*.
                                - Sistemas: fluxo *clientCredentials*, cliente `sistema-pagamentos`.
                                - Analistas: fluxo *authorizationCode*, cliente `painel-analistas`, usuário `analista`.

                                Os segredos e senhas do ambiente local estão em `seguranca/keycloak/LEIA-ME.md`.
                                """)
                        .license(new License().name("Projeto de estudo")))
                .components(new Components().addSecuritySchemes(ESQUEMA_SEGURANCA, new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .description("Tokens JWT emitidos pelo Keycloak (realm motor-risco)")
                        .flows(new OAuthFlows()
                                .clientCredentials(new OAuthFlow()
                                        .tokenUrl(oidc + "/token")
                                        .scopes(new Scopes()))
                                .authorizationCode(new OAuthFlow()
                                        .authorizationUrl(oidc + "/auth")
                                        .tokenUrl(oidc + "/token")
                                        .scopes(new Scopes())))))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_SEGURANCA));
    }
}
