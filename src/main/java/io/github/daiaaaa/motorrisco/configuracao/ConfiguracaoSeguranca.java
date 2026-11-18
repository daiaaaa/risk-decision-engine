package io.github.daiaaaa.motorrisco.configuracao;

import io.github.daiaaaa.motorrisco.adaptador.entrada.web.seguranca.ConversorPapeisKeycloak;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Quem pode chamar o quê. A aplicação é um OAuth2 Resource Server: não faz login, só valida
 * o JWT emitido pelo Keycloak (assinatura, validade, emissor e audiência, configurados em
 * application.properties).
 *
 * <table>
 *   <tr><th>Endpoint</th><th>Papel</th><th>Quem tem</th></tr>
 *   <tr><td>POST /avaliacoes</td><td>avaliador</td><td>sistemas que pedem decisão (ex.: pagamentos)</td></tr>
 *   <tr><td>GET /revisoes</td><td>analista</td><td>analistas de fraude</td></tr>
 *   <tr><td>/actuator/**</td><td>monitoramento</td><td>Prometheus</td></tr>
 *   <tr><td>/actuator/health/**</td><td>nenhum</td><td>probes do orquestrador (sem token)</td></tr>
 * </table>
 */
@Configuration
public class ConfiguracaoSeguranca {

    public static final String AVALIADOR = "avaliador";
    public static final String ANALISTA = "analista";
    public static final String MONITORAMENTO = "monitoramento";

    @Bean
    public SecurityFilterChain filtrosSeguranca(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(regras -> regras
                        // Probes não têm token; o detalhe da saúde só aparece para MONITORAMENTO.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole(MONITORAMENTO)
                        .requestMatchers(HttpMethod.POST, "/avaliacoes").hasRole(AVALIADOR)
                        .requestMatchers(HttpMethod.GET, "/revisoes").hasRole(ANALISTA)
                        // Página de erro do Spring, usada quando uma resposta de erro é montada.
                        .requestMatchers("/error").permitAll()
                        // Qualquer endpoint novo nasce fechado.
                        .anyRequest().denyAll())
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ConversorPapeisKeycloak())))
                // API sem sessão: cada requisição traz o próprio token.
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF protege contra cookies enviados automaticamente pelo navegador. Aqui o token
                // vai no cabeçalho Authorization, então não há cookie a proteger.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
