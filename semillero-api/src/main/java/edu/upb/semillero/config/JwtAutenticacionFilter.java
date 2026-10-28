package edu.upb.semillero.config;

import edu.upb.semillero.service.seguridad.JwtService;
import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tarea S2-01: lee el {@code Authorization: Bearer <token>}, lo valida y deja
 * al usuario en el contexto de seguridad con sus roles como authorities
 * {@code ROLE_<CODIGO>}.
 *
 * <p>Si no hay token, o no sirve, no autentica a nadie y sigue: de ahí en más
 * decide {@code SeguridadConfiguration}, que es quien sabe qué rutas son
 * públicas.
 */
@Component
public class JwtAutenticacionFilter extends OncePerRequestFilter {
    private static final String PREFIJO = "Bearer ";

    private final JwtService jwt;

    public JwtAutenticacionFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String cabecera = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.startsWith(PREFIJO)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwt.validar(cabecera.substring(PREFIJO.length()).trim())
                    .ifPresent(usuario -> autenticar(request, usuario));
        }
        chain.doFilter(request, response);
    }

    private void autenticar(HttpServletRequest request, UsuarioAutenticado usuario) {
        List<SimpleGrantedAuthority> authorities = usuario.roles().stream()
                .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol))
                .toList();
        var autenticacion = new UsernamePasswordAuthenticationToken(usuario, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
        request.setAttribute("usuarioId", usuario.usuarioId());
    }
}
