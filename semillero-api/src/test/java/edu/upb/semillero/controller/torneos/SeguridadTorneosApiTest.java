package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.entity.Auditoria;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Rol;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.UsuarioClub;
import edu.upb.semillero.repository.entity.UsuarioRol;
import edu.upb.semillero.repository.entity.UsuarioTrabaja;
import edu.upb.semillero.repository.repos.AuditoriaRepository;
import edu.upb.semillero.repository.repos.ClubRepository;
import edu.upb.semillero.repository.repos.RolRepository;
import edu.upb.semillero.repository.repos.UsuarioClubRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.repository.repos.UsuarioRolRepository;
import edu.upb.semillero.repository.repos.UsuarioTrabajaRepository;
import edu.upb.semillero.service.comun.Roles;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 16 - Control de acceso por roles: tareas S2-01 (JWT), S2-02 (permisos por
 * endpoint), S2-03 (restricción por propiedad) y S2-04 (registro de accesos
 * rechazados).
 *
 * <p>Sin {@code @WithMockUser} a propósito: acá el token se saca del login, que
 * es lo que hace la web y la app.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadTorneosApiTest {
    private static final String TORNEOS = "/api/v1/torneos";
    private static final String CLAVE = "secreto123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired UsuarioRolRepository usuariosRoles;
    @Autowired ClubRepository clubes;
    @Autowired UsuarioClubRepository usuariosClub;
    @Autowired UsuarioTrabajaRepository usuariosTrabaja;
    @Autowired AuditoriaRepository auditoria;
    @Autowired PasswordEncoder passwordEncoder;

    /** Tarea S2-01: el login devuelve el token y los roles de la HU 16. */
    @Test
    void elLoginDevuelveElTokenYLosRoles() throws Exception {
        Usuario organizador = usuarioCon(Roles.ORGANIZADOR);

        String cuerpo = login(organizador).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.roles[0]").value(Roles.ORGANIZADOR))
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(cuerpo).get("token").asString().split("\\.")).hasSize(3);
    }

    /** Tarea S2-02: sin token no se entra a torneos, y con token basura tampoco. */
    @Test
    void sinTokenNoSeEntraALosEndpointsDeTorneos() throws Exception {
        mvc.perform(get(TORNEOS)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("iniciar sesión")));
        mvc.perform(get(TORNEOS).header(HttpHeaders.AUTHORIZATION, "Bearer no.es.un.token"))
                .andExpect(status().isUnauthorized());

        // Los endpoints de los otros módulos siguen abiertos.
        mvc.perform(get("/api/v1/paises")).andExpect(status().isOk());
    }

    /** Tarea S2-02: el rol CONSULTA lee el fixture pero no crea torneos. */
    @Test
    void elRolConsultaLeePeroNoEscribe() throws Exception {
        String token = token(usuarioCon(Roles.CONSULTA));

        mvc.perform(get(TORNEOS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post(TORNEOS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("no tiene permiso")));
    }

    /** Tarea S2-02: el entrenador maneja su lista de buena fe, no el torneo. */
    @Test
    void elEntrenadorEntraALaListaDeBuenaFePeroNoAlTorneo() throws Exception {
        String token = token(usuarioCon(Roles.ENTRENADOR));

        mvc.perform(post(TORNEOS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // Llega al servicio (404 de la inscripción), o sea que el rol no lo frenó.
        mvc.perform(post(TORNEOS + "/inscripciones/999999/lista-buena-fe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jugadorId\":1,\"dorsal\":10}"))
                .andExpect(status().isNotFound());
    }

    /** Tarea S2-04: cada rechazo queda en auditoría con la ruta y el motivo. */
    @Test
    void elAccesoRechazadoQuedaRegistrado() throws Exception {
        long antes = auditoria.findByModuloOrderByFechaDesc("TORNEOS").stream()
                .filter(fila -> "ACCESO_DENEGADO".equals(fila.getAccion())).count();

        mvc.perform(get(TORNEOS + "/sedes")).andExpect(status().isUnauthorized());

        List<Auditoria> rechazos = auditoria.findByModuloOrderByFechaDesc("TORNEOS").stream()
                .filter(fila -> "ACCESO_DENEGADO".equals(fila.getAccion())).toList();
        assertThat(rechazos).hasSizeGreaterThan((int) antes);
        assertThat(rechazos.get(0).getValorNuevo()).contains("/api/v1/torneos/sedes");
        assertThat(rechazos.get(0).getMotivo()).isNotBlank();
        assertThat(rechazos.get(0).getTabla()).isEqualTo("api");
    }

    /**
     * Tarea S2-03: el entrenador de un club no puede cargar la lista de buena fe
     * del equipo de otro club.
     */
    @Test
    void elEntrenadorNoTocaLaListaDeOtroClub() throws Exception {
        Club suyo = club("Club del entrenador");
        Club ajeno = club("Club ajeno");
        Usuario entrenador = entrenadorDe(suyo);
        String token = token(entrenador);

        long organizadorId = idDe(postJson("/api/v1/usuarios", Map.of("name", "org" + System.nanoTime(),
                "nombres", "Org", "apellidos", "Org",
                "email", "org" + System.nanoTime() + "@example.com", "password", CLAVE))
                .andExpect(status().isCreated()));
        long inscripcionAjena = inscripcionDe(ajeno, organizadorId);

        mvc.perform(post(TORNEOS + "/inscripciones/" + inscripcionAjena + "/lista-buena-fe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jugadorId\":1,\"dorsal\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("entrenador de otro club")));

        assertThat(auditoria.findByModuloOrderByFechaDesc("TORNEOS").stream()
                .filter(fila -> "ACCESO_DENEGADO".equals(fila.getAccion()))
                .anyMatch(fila -> fila.getMotivo() != null
                        && fila.getMotivo().contains("no trabaja en el club"))).isTrue();
    }

    /** Tarea S2-02: los roles los asigna el administrador, y solo él. */
    @Test
    void soloElAdministradorAsignaRoles() throws Exception {
        Usuario admin = usuarioCon(Roles.ADMIN);
        Usuario cualquiera = nuevoUsuario();
        String tokenAdmin = token(admin);

        mvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/roles").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(usuarioCon(Roles.CONSULTA))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.hasItem(Roles.MESA_CONTROL)));

        mvc.perform(put("/api/v1/usuarios/" + cualquiera.getId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"MESA_CONTROL\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(Roles.MESA_CONTROL));
        mvc.perform(put("/api/v1/usuarios/" + cualquiera.getId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"NO_EXISTE\"]"))
                .andExpect(status().isNotFound());
    }

    private long inscripcionDe(Club club, long organizadorId) throws Exception {
        Map<String, Object> torneo = new LinkedHashMap<>();
        torneo.put("nombre", "Copa Seguridad");
        torneo.put("temporada", java.time.LocalDate.now().getYear());
        torneo.put("tipo", "OFICIAL");
        torneo.put("ambito", "INTERNO");
        torneo.put("formato", "LIGA");
        torneo.put("fechaInicio", java.time.LocalDate.now().plusDays(20).toString());
        torneo.put("fechaFin", java.time.LocalDate.now().plusDays(100).toString());
        torneo.put("fechaLimiteInscripcion", java.time.LocalDate.now().plusDays(10).toString());
        torneo.put("usuarioId", organizadorId);

        String tokenOrganizador = token(usuarioCon(Roles.ORGANIZADOR));
        long torneoId = idDe(mvc.perform(post(TORNEOS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOrganizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(torneo)))
                .andExpect(status().isCreated()));

        Map<String, Object> categoria = new LinkedHashMap<>();
        categoria.put("nombre", "Sub-14");
        categoria.put("edadMaxima", 14);
        categoria.put("cupoMaximo", 4);
        categoria.put("duracionTiempoMin", 35);
        categoria.put("jugadoresEnCancha", 11);
        long categoriaId = idDe(mvc.perform(post(TORNEOS + "/" + torneoId + "/categorias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOrganizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(categoria)))
                .andExpect(status().isCreated()));
        mvc.perform(post(TORNEOS + "/" + torneoId + "/abrir-inscripciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOrganizador))
                .andExpect(status().isOk());

        Map<String, Object> inscripcion = Map.of("torneoCategoriaId", categoriaId,
                "clubId", club.getId(), "usuarioId", organizadorId);
        return idDe(mvc.perform(post(TORNEOS + "/inscripciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOrganizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(inscripcion)))
                .andExpect(status().isCreated()));
    }

    private Usuario usuarioCon(String codigoRol) {
        Usuario usuario = nuevoUsuario();
        Rol rol = roles.findByCodigo(codigoRol).orElseThrow();
        UsuarioRol asignacion = new UsuarioRol();
        asignacion.setUsuario(usuario);
        asignacion.setRol(rol);
        asignacion.setFechaAlta(LocalDateTime.now());
        usuariosRoles.saveAndFlush(asignacion);
        return usuario;
    }

    /** Entrenador de verdad: el rol sale de usuario_trabaja, como el punto 21 del acuerdo. */
    private Usuario entrenadorDe(Club club) {
        Usuario usuario = nuevoUsuario();
        UsuarioClub usuarioClub = new UsuarioClub();
        usuarioClub.setUsuario(usuario);
        usuariosClub.saveAndFlush(usuarioClub);
        UsuarioTrabaja vinculo = new UsuarioTrabaja();
        vinculo.setUsuarioClub(usuarioClub);
        vinculo.setClub(club);
        vinculo.setTipoUsuarioClub("EN");
        usuariosTrabaja.saveAndFlush(vinculo);
        return usuario;
    }

    private Usuario nuevoUsuario() {
        Usuario usuario = new Usuario();
        usuario.setName("u" + System.nanoTime());
        usuario.setNombres("Test");
        usuario.setApellidos("Test");
        usuario.setEmail("seg" + System.nanoTime() + "@example.com");
        usuario.setPasswordHash(passwordEncoder.encode(CLAVE));
        return usuarios.saveAndFlush(usuario);
    }

    private Club club(String nombre) {
        Club club = new Club();
        club.setNombreClub(nombre + System.nanoTime() % 1000);
        return clubes.saveAndFlush(club);
    }

    private String token(Usuario usuario) throws Exception {
        String cuerpo = login(usuario).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("token").asString();
    }

    private ResultActions login(Usuario usuario) throws Exception {
        return postJson("/api/v1/auth/login",
                Map.of("identificador", usuario.getEmail(), "password", CLAVE));
    }

    private long idDe(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private ResultActions postJson(String url, Map<String, Object> body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }
}
