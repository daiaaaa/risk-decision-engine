package io.github.daiaaaa.motorrisco.adaptador.entrada.web;

import io.github.daiaaaa.motorrisco.adaptador.entrada.web.seguranca.ConversorPapeisKeycloak;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Tokens de teste no mesmo formato que o Keycloak emite (papéis em realm_access.roles).
 * Os papéis passam pelo conversor de verdade, então o mapeamento também é testado.
 * A validação de assinatura, emissor e audiência fica de fora: é feita pelo Spring Security
 * e conferida na demonstração com o Keycloak real.
 */
public final class Tokens {

    private Tokens() {
    }

    public static JwtRequestPostProcessor avaliador() {
        return comPapel("sistema-pagamentos", "avaliador");
    }

    public static JwtRequestPostProcessor analista() {
        return comPapel("painel-analistas", "analista");
    }

    public static JwtRequestPostProcessor monitoramento() {
        return comPapel("prometheus", "monitoramento");
    }

    private static JwtRequestPostProcessor comPapel(String cliente, String papel) {
        return jwt()
                .jwt(token -> token
                        .claim("azp", cliente)
                        .claim("aud", List.of("motor-risco-api"))
                        .claim("realm_access", Map.of("roles", List.of(papel))))
                .authorities(ConversorPapeisKeycloak::papeis);
    }
}
