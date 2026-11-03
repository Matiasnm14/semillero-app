package edu.upb.semillero;

import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;

/** Identidades explícitas para fixtures; las pruebas de autenticación usan JWT reales. */
public final class SesionesDePrueba {
    private SesionesDePrueba() {}

    public static void autenticar(Long usuarioId, String... roles) {
        List<String> permisos = List.copyOf(Arrays.asList(roles));
        var principal = new UsuarioAutenticado(usuarioId, "fixture-" + usuarioId, permisos, null);
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, permisos.stream()
                        .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol)).toList()));
    }

    public static <T> T como(Long usuarioId, String rol, Callable<T> action) throws Exception {
        Authentication anterior = TestSecurityContextHolder.getContext().getAuthentication();
        try {
            autenticar(usuarioId, rol);
            return action.call();
        } finally {
            if (anterior == null) TestSecurityContextHolder.clearContext();
            else TestSecurityContextHolder.setAuthentication(anterior);
        }
    }
}
