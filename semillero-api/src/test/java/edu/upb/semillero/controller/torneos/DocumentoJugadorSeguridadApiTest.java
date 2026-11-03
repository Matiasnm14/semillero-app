package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.entity.DocumentoJugador;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Rol;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.UsuarioRol;
import edu.upb.semillero.repository.enums.EstadoDocumentoJugador;
import edu.upb.semillero.repository.enums.EstadoJugador;
import edu.upb.semillero.repository.enums.TipoDocumentoJugador;
import edu.upb.semillero.repository.repos.DocumentoJugadorRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.RolRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.repository.repos.UsuarioRolRepository;
import edu.upb.semillero.service.comun.Roles;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Correcciones del PR #13 (punto 2): los documentos del jugador llevan datos
 * sensibles y {@code FichaTecnicaService} los usa para habilitar la lista de
 * buena fe. El recurso exige sesión, controla por jugador quién lo administra y
 * reserva la aprobación a VIGENTE al administrador. Con token de verdad.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentoJugadorSeguridadApiTest {
    private static final String CLAVE = "secreto123";
    private static final String BASE = "/api/v1/documentos-jugador";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired JugadorRepository jugadores;
    @Autowired DocumentoJugadorRepository documentos;
    @Autowired RolRepository roles;
    @Autowired UsuarioRolRepository usuariosRoles;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void sinTokenRechazaTodasLasOperaciones() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/1")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(doc(1, "APTO_MEDICO", "VIGENTE")))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(doc(1, "CI", "RECHAZADO")))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(BASE + "/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void elPropietarioAdministraSusDocumentosPeroNoApruebaVigente() throws Exception {
        Usuario dueno = usuarioCon(Roles.CONSULTA);
        long jugador = jugadorDe(dueno);
        String token = token(dueno);

        String creado = con(token, post(BASE), doc(jugador, "CI", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        long docId = json.readTree(creado).get("id").asLong();

        mvc.perform(get(BASE + "/" + docId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        // Subir no aprueba: enviar VIGENTE sin ser administrador da 403.
        con(token, post(BASE), doc(jugador, "APTO_MEDICO", "VIGENTE")).andExpect(status().isForbidden());
        con(token, put(BASE + "/" + docId), doc(jugador, "CI", "VIGENTE")).andExpect(status().isForbidden());
        // Un estado que no es aprobación sí lo puede poner el propietario.
        con(token, put(BASE + "/" + docId), doc(jugador, "CI", "RECHAZADO")).andExpect(status().isOk());
        assertThat(documentos.findById(docId).orElseThrow().getEstado())
                .isEqualTo(EstadoDocumentoJugador.RECHAZADO);
    }

    @Test
    void noAccedeNiTrasladaDocumentosDeOtroJugador() throws Exception {
        Usuario dueno = usuarioCon(Roles.CONSULTA);
        Usuario ajeno = usuarioCon(Roles.CONSULTA);
        long jugadorDueno = jugadorDe(dueno);
        long jugadorAjeno = jugadorDe(ajeno);
        long docAjeno = documentoDe(jugadorAjeno);
        long docPropio = documentoDe(jugadorDueno);
        String token = token(dueno);

        mvc.perform(get(BASE + "/" + docAjeno).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        con(token, post(BASE), doc(jugadorAjeno, "CI", null)).andExpect(status().isForbidden());
        // No se puede trasladar un documento propio a un jugador ajeno para eludir el control.
        con(token, put(BASE + "/" + docPropio), doc(jugadorAjeno, "CI", null)).andExpect(status().isForbidden());

        assertThat(documentos.findById(docAjeno).orElseThrow().getJugador().getId()).isEqualTo(jugadorAjeno);
        assertThat(documentos.findById(docPropio).orElseThrow().getJugador().getId()).isEqualTo(jugadorDueno);
    }

    @Test
    void elAdministradorApruebaYBorra() throws Exception {
        Usuario admin = usuarioCon(Roles.ADMIN);
        long jugador = jugadorDe(usuarioCon(Roles.CONSULTA));
        String token = token(admin);

        String creado = con(token, post(BASE), doc(jugador, "APTO_MEDICO", "VIGENTE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andReturn().getResponse().getContentAsString();
        long docId = json.readTree(creado).get("id").asLong();

        con(token, put(BASE + "/" + docId), doc(jugador, "APTO_MEDICO", "VIGENTE")).andExpect(status().isOk());
        mvc.perform(delete(BASE + "/" + docId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private String doc(long jugadorId, String tipo, String estado) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jugadorId", jugadorId);
        body.put("tipo", tipo);
        body.put("fechaVencimiento", LocalDate.now().plusYears(1).toString());
        if (estado != null) {
            body.put("estado", estado);
        }
        return json.writeValueAsString(body);
    }

    private ResultActions con(String token, MockHttpServletRequestBuilder pedido, String body) throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Usuario usuarioCon(String codigoRol) {
        Usuario usuario = new Usuario();
        usuario.setName("u" + System.nanoTime());
        usuario.setNombres("Documento");
        usuario.setApellidos("Prueba");
        usuario.setEmail("doc" + System.nanoTime() + "@example.com");
        usuario.setPasswordHash(passwordEncoder.encode(CLAVE));
        usuario = usuarios.saveAndFlush(usuario);
        Rol rol = roles.findByCodigo(codigoRol).orElseThrow();
        UsuarioRol asignacion = new UsuarioRol();
        asignacion.setUsuario(usuario);
        asignacion.setRol(rol);
        asignacion.setFechaAlta(LocalDateTime.now());
        usuariosRoles.saveAndFlush(asignacion);
        return usuario;
    }

    private long jugadorDe(Usuario usuario) {
        Jugador jugador = new Jugador();
        jugador.setUsuario(usuario);
        jugador.setPiernaHabil("D");
        jugador.setFechaNacimiento(LocalDate.now().minusYears(13));
        jugador.setEstado(EstadoJugador.ACTIVO);
        return jugadores.saveAndFlush(jugador).getId();
    }

    private long documentoDe(long jugadorId) {
        DocumentoJugador documento = new DocumentoJugador();
        documento.setJugador(jugadores.findById(jugadorId).orElseThrow());
        documento.setTipo(TipoDocumentoJugador.CI);
        documento.setEstado(EstadoDocumentoJugador.PENDIENTE);
        return documentos.saveAndFlush(documento).getId();
    }

    private String token(Usuario usuario) throws Exception {
        String cuerpo = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("identificador", usuario.getEmail(),
                                "password", CLAVE))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("token").asString();
    }
}
