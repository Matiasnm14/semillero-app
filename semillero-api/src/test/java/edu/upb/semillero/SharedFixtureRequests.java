package edu.upb.semillero;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/** Shared master records are administrative fixtures, not public signup operations. */
public final class SharedFixtureRequests {
    private SharedFixtureRequests() {}

    public static MockHttpServletRequestBuilder post(String url) {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
        boolean masterFixture = java.util.Set.of("/api/v1/paises", "/api/v1/ciudades",
                "/api/v1/clubes", "/api/v1/jugadores", "/api/v1/posiciones",
                "/api/v1/usuarios-club", "/api/v1/usuarios-trabaja").contains(url);
        return masterFixture ? request.with(user("shared-fixture-admin").roles("ADMIN")) : request;
    }
}
