package io.github.daiaaaa.motorrisco.adaptador.entrada.web.seguranca;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Transforma os papéis do Keycloak em autoridades do Spring Security.
 *
 * <p>O Keycloak coloca os papéis do realm em {@code realm_access.roles}; o Spring, por padrão,
 * só lê o claim {@code scope}. Cada papel vira {@code ROLE_<papel>}, que é o formato esperado
 * por {@code hasRole(...)}.
 *
 * <p>O nome do usuário autenticado vem do {@code azp} (o cliente que pediu o token) quando
 * o token é de máquina, ou do {@code preferred_username} quando é de uma pessoa. Isso aparece
 * nos logs e facilita a auditoria.
 */
public class ConversorPapeisKeycloak implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, papeis(jwt), nome(jwt));
    }

    public static Collection<GrantedAuthority> papeis(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> papeis)) {
            return List.of();
        }
        return papeis.stream()
                .map(String::valueOf)
                .<GrantedAuthority>map(papel -> new SimpleGrantedAuthority("ROLE_" + papel))
                .toList();
    }

    private static String nome(Jwt jwt) {
        String usuario = jwt.getClaimAsString("preferred_username");
        if (usuario != null && !usuario.startsWith("service-account-")) {
            return usuario;
        }
        String cliente = jwt.getClaimAsString("azp");
        return cliente != null ? cliente : jwt.getSubject();
    }
}
