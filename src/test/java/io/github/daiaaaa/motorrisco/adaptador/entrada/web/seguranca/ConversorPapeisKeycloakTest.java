package io.github.daiaaaa.motorrisco.adaptador.entrada.web.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversorPapeisKeycloakTest {

    private final ConversorPapeisKeycloak conversor = new ConversorPapeisKeycloak();

    @Test
    void papeisDoRealmViramRoles() {
        Jwt token = token(Map.of(
                "azp", "sistema-pagamentos",
                "preferred_username", "service-account-sistema-pagamentos",
                "realm_access", Map.of("roles", List.of("avaliador", "default-roles-motor-risco"))));

        AbstractAuthenticationToken autenticacao = conversor.convert(token);

        assertThat(autenticacao.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_avaliador", "ROLE_default-roles-motor-risco");
        // Token de máquina: o nome é o cliente, não o usuário técnico "service-account-...".
        assertThat(autenticacao.getName()).isEqualTo("sistema-pagamentos");
    }

    @Test
    void tokenDePessoaUsaONomeDoUsuario() {
        Jwt token = token(Map.of(
                "azp", "painel-analistas",
                "preferred_username", "analista",
                "realm_access", Map.of("roles", List.of("analista"))));

        assertThat(conversor.convert(token).getName()).isEqualTo("analista");
    }

    @Test
    void tokenSemPapeisNaoRecebeNenhumaAutoridade() {
        Jwt token = token(Map.of("azp", "outro-sistema"));

        assertThat(conversor.convert(token).getAuthorities()).isEmpty();
    }

    private static Jwt token(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("sub-123")
                .issuedAt(Instant.parse("2026-11-10T12:00:00Z"))
                .expiresAt(Instant.parse("2026-11-10T12:05:00Z"))
                .claims(c -> c.putAll(claims))
                .build();
    }
}
