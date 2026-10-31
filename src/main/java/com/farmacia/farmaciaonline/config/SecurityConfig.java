package com.farmacia.farmaciaonline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.farmacia.farmaciaonline.security.LoginFailureHandler;
import com.farmacia.farmaciaonline.security.LoginSuccessHandler;

/**
 * Regras de acesso do site.
 *
 * - Públicas: vitrine, carrinho, login, cadastro, páginas institucionais e arquivos estáticos.
 * - /admin/** e /demo/** : só o papel ADMIN (cliente logado recebe 403; visitante é mandado para /login).
 * - O resto exige login.
 *
 * O CSRF fica LIGADO (padrão do Spring): todo formulário POST precisa do token, e o Thymeleaf
 * o inclui sozinho quando o formulário usa {@code th:action}. O logout é um POST pelo mesmo motivo.
 *
 * Quem carrega o usuário no login é o {@code UsuarioDetailsService} (o Spring o encontra
 * sozinho, por ser o único UserDetailsService da aplicação) junto com o PasswordEncoder abaixo.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] ROTAS_PUBLICAS = {
            "/",
            "/produtos/**",
            "/carrinho/**",
            "/login",
            "/cadastro/**",
            "/confirmar-conta/**",
            "/recuperar-senha/**",
            "/caixa-demo/**", // caixa de mensagens de teste: a rota só existe no perfil demo
            "/institucional/**", // páginas do rodapé (já existem desde a F1-07)
            "/css/**",
            "/images/**",
            "/uploads/**", // fotos enviadas pelo admin (UC12)
            "/js/**",
            "/error" // o Spring Boot mostra aqui as páginas de erro, incluindo templates/error/403.html
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
                .successHandler(new LoginSuccessHandler())
                .failureHandler(new LoginFailureHandler())
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout") // com CSRF ligado, só aceita POST
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
