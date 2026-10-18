package com.mdm.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(JdbcTemplate jdbc) {
        return email -> jdbc.query("SELECT email,password_hash,role FROM users WHERE email=?",
            (rs,n) -> new SessionPasswordVersions.VersionedUser(rs.getString(1),rs.getString(2),rs.getString(3)), email).stream().findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
    SecurityFilterChain security(HttpSecurity http,RateLimits limits,SessionPasswordVersions versions,@Value("${mdm.site.base-url:http://localhost:8080}") String baseUrl,@Value("${mdm.security.production:false}") boolean production) throws Exception {
        if(production)http.redirectToHttps(Customizer.withDefaults());
        return http.authorizeHttpRequests(a -> a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/login","/forgot-password","/reset-password","/welcome","/privacy","/terms","/robots.txt","/sitemap.xml","/analytics/pageview","/style.css","/assets/**","/error").permitAll()
                .requestMatchers("/admin","/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.accessDeniedHandler((request,response,error)->{
                String path=request.getRequestURI().substring(request.getContextPath().length());
                if("POST".equals(request.getMethod())&&"/login".equals(path)&&error instanceof org.springframework.security.web.csrf.CsrfException){
                    // Refresh the form; never authenticate a request with a missing or stale token.
                    org.slf4j.LoggerFactory.getLogger(SecurityConfig.class).info("Login form rejected: missing or expired CSRF token. A fresh sign-in form is required.");
                    response.sendRedirect(request.getContextPath()+"/login?formExpired");
                }else response.sendError(403);
            }))
            .formLogin(f -> f.loginPage("/login").successHandler((request,response,authentication)->{
                if(!(authentication.getPrincipal() instanceof SessionPasswordVersions.VersionedUser user))throw new IllegalStateException("Password version is required for a signed-in session");
                versions.record(request.getSession(),user.version());response.sendRedirect(request.getContextPath()+"/");
            }).permitAll())
            .logout(l -> l.logoutSuccessUrl("/login?logout"))
            .addFilterBefore(new RequestProtection.Headers(baseUrl,production),CsrfFilter.class)
            .addFilterBefore(new RequestProtection.Throttle(limits),UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new PasswordVersionFilter(versions),org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
            .build();
    }
}
