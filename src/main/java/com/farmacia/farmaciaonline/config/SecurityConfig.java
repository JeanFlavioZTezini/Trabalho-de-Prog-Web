package com.farmacia.farmaciaonline.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import com.farmacia.farmaciaonline.security.LimiteDeTentativasEmMemoria;
import com.farmacia.farmaciaonline.security.LoginFailureHandler;
import com.farmacia.farmaciaonline.security.LoginSuccessHandler;
import com.farmacia.farmaciaonline.security.LoginTentativasService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] ROTAS_PUBLICAS = {
            "/",
            "/produtos/**",
            "/carrinho/**",
            "/login",
            "/login/social/**", // F-20: login social; sem provedor (fora do perfil demo) as rotas dão 404
            "/cadastro/**",
            "/confirmar-conta/**",
            "/recuperar-senha/**",
            "/caixa-demo/**", // caixa de mensagens de teste: a rota só existe no perfil demo
            "/institucional/**", // páginas do rodapé (já existem desde a F1-07)
            "/css/**",
            "/images/**",
            "/uploads/**", // fotos enviadas pelo admin (UC12)
            "/js/**",
            "/robots.txt", // orientação para buscadores (static/robots.txt)
            "/error" // o Spring Boot mostra aqui as páginas de erro, incluindo templates/error/403.html
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry,
            ObjectProvider<LoginTentativasService> tentativasProvider,
            ObjectProvider<LimiteDeTentativasEmMemoria> tentativasEmMemoriaProvider) throws Exception {
        // Opcional de propósito: os testes de camada web (@WebMvcTest + SecurityConfig) não carregam o serviço, e os handlers
        // aceitam null (sem contagem de falhas).
        LoginTentativasService loginTentativasService = tentativasProvider.getIfAvailable();
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(ROTAS_PUBLICAS).permitAll()
                .requestMatchers("/admin/**", "/demo/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("identificador") // e-mail ou CPF
                .passwordParameter("senha")
                .successHandler(new LoginSuccessHandler(loginTentativasService))
                .failureHandler(new LoginFailureHandler(loginTentativasService, tentativasEmMemoriaProvider.getIfAvailable()))
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout") // com CSRF ligado, só aceita POST
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            // Registro de todas as sessões: a recuperação de senha (F-06) encerra as sessões da conta, e a sessão
            // encerrada é barrada na próxima requisição e vai para /login?expirada. Sem limite de sessões por pessoa.
            .sessionManagement(sessao -> sessao
                .sessionConcurrency(concorrencia -> concorrencia
                    .maximumSessions(-1)
                    .sessionRegistry(sessionRegistry)
                    .expiredUrl("/login?expirada")));
        return http.build();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Avisa o registro quando uma sessão termina (logout, tempo esgotado), para ele não guardar sessões mortas. */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
