package edu.upb.semillero.controller.scouting;

import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.entity.scouting.*;
import edu.upb.semillero.repository.enums.EstadoJugador;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.repository.repos.scouting.*;
import edu.upb.semillero.service.seguridad.JwtService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=${SCOUTING_TEST_URL:jdbc:h2:mem:scouting;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${SCOUTING_TEST_USER:sa}",
    "scouting.correo.remitente=contacto@semillero.example.test",
    "scouting.correo.url-publica=https://semillero.example.test"
})
@AutoConfigureMockMvc @ActiveProfiles("test")
class ScoutingContactosApiTest {
    private static final String API = "/api/v1/scouting";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UsuarioRepository usuarios;
    @Autowired JugadorRepository jugadores;
    @Autowired ClubRepository clubes;
    @Autowired UsuarioClubRepository usuariosClub;
    @Autowired UsuarioTrabajaRepository trabajos;
    @Autowired PreferenciaContactoRepository preferencias;
    @Autowired SolicitudContactoRepository solicitudes;
    @Autowired EventoContactoRepository eventos;
    @Autowired CorreoContactoRepository correos;
    @Autowired AccesoContactoRepository accesos;
    @Autowired JugadorGuardadoRepository guardados;
    @Autowired PosicionRepository posiciones;
    @Autowired PosicionJugadorRepository posicionesJugador;
    @Autowired FichadoRepository fichados;
    @Autowired edu.upb.semillero.service.scouting.PreparadorCorreoContacto preparador;

    record Caso(Usuario representante, Club club, Usuario usuarioJugador, Jugador jugador) {}
    private Usuario usuario() {
        var u = new Usuario(); u.setName(UUID.randomUUID().toString());
        u.setNombres("Nombre" + UUID.randomUUID().toString().substring(0, 8)); u.setApellidos("Apellido");
        u.setEmail(u.getName() + "@example.test"); u.setTelefonoUsuario("70000000");
        u.setDocIdentidadUsuario("documento-privado"); u.setPasswordHash("no-usado-en-test");
        return usuarios.saveAndFlush(u);
    }
    private Caso caso() {
        var r = usuario(); var u = usuario();
        var c = new Club(); c.setNombreClub("Club " + r.getId()); c = clubes.saveAndFlush(c);
        var uc = new UsuarioClub(); uc.setUsuario(r); uc = usuariosClub.saveAndFlush(uc);
        var t = new UsuarioTrabaja(); t.setUsuarioClub(uc); t.setClub(c); t.setTipoUsuarioClub("DI"); trabajos.saveAndFlush(t);
        var j = new Jugador(); j.setUsuario(u); j.setFechaNacimiento(LocalDate.now(ZoneOffset.UTC).minusYears(20));
        j = jugadores.saveAndFlush(j);
        var p = new PreferenciaContacto(); p.setJugadorId(j.getId()); p.setVisible(true);
        p.setAceptaSolicitudes(true); p.setActualizadaEn(Instant.now()); preferencias.saveAndFlush(p);
        return new Caso(r,c,u,j);
    }
    private String auth(Usuario u) { return "Bearer " + jwt.emitir(u.getId(),u.getName(),List.of("CONSULTA"),null); }
    private ResultActions crear(Caso c, UUID key, Usuario actor) throws Exception {
        return mvc.perform(post(API + "/solicitudes").header("Authorization",auth(actor))
            .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("clubId",c.club().getId(),"jugadorId",c.jugador().getId(),"motivo","Invitación a prueba deportiva"))));
    }
    private String nueva(Caso c) throws Exception {
        return json.readTree(crear(c,UUID.randomUUID(),c.representante()).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString()).get("id").asString();
    }
    private ResultActions resolver(Caso c, String id, Usuario actor, String estado, long version) throws Exception {
        return mvc.perform(post(API + "/solicitudes/" + id + "/respuesta").header("Authorization",auth(actor))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("estado",estado,"version",version,"motivo","Decisión del titular"))));
    }
    private ResultActions datos(String id, Usuario u) throws Exception {
        return mvc.perform(post(API + "/solicitudes/" + id + "/datos-contacto").header("Authorization",auth(u))
            .contentType(MediaType.APPLICATION_JSON).content("{\"proposito\":\"Coordinar prueba aceptada\"}"));
    }

    @Test void requiereTokenSinCambiarRutasCompartidas() throws Exception {
        mvc.perform(get(API + "/jugadores")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get(API + "/jugadores").header("Authorization","Bearer invalido")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/paises")).andExpect(status().isOk());
    }
    @Test void directorioNoEntregaDatosPrivadosYLeeCambiosDelPerfilMaestro() throws Exception {
        var c = caso();
        mvc.perform(get(API + "/jugadores/" + c.jugador().getId()).header("Authorization",auth(c.representante())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nombres").value(c.usuarioJugador().getNombres()))
            .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.telefonoUsuario").doesNotExist())
            .andExpect(jsonPath("$.fechaNacimiento").doesNotExist()).andExpect(jsonPath("$.docIdentidadUsuario").doesNotExist());
        var u = usuarios.findById(c.usuarioJugador().getId()).orElseThrow(); u.setNombres("Perfil actualizado"); usuarios.saveAndFlush(u);
        mvc.perform(get(API + "/jugadores/" + c.jugador().getId()).header("Authorization",auth(c.representante())))
            .andExpect(jsonPath("$.nombres").value("Perfil actualizado"));
    }
    @Test void ocultoYDesconocidoRespondenIgual() throws Exception {
        var c = caso(); var p = preferencias.findById(c.jugador().getId()).orElseThrow(); p.setVisible(false); p.setAceptaSolicitudes(false); preferencias.saveAndFlush(p);
        mvc.perform(get(API + "/jugadores/" + c.jugador().getId()).header("Authorization",auth(c.representante()))).andExpect(status().isNotFound());
        mvc.perform(get(API + "/jugadores/99999999").header("Authorization",auth(c.representante()))).andExpect(status().isNotFound());
    }
    @Test void preferenciasSoloDelTitularConControlDeVersion() throws Exception {
        var c = caso();
        mvc.perform(put(API + "/mi-perfil/preferencias").header("Authorization",auth(c.usuarioJugador()))
            .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":false,\"aceptaSolicitudes\":false,\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put(API + "/mi-perfil/preferencias").header("Authorization",auth(c.usuarioJugador()))
            .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":true,\"aceptaSolicitudes\":true,\"version\":0}"))
            .andExpect(status().isConflict());
        mvc.perform(get(API + "/mi-perfil/preferencias").header("Authorization",auth(c.representante()))).andExpect(status().isForbidden());
    }
    @Test void preferenciasPorDefectoPrivadasYAltaInicial() throws Exception {
        var c = caso(); preferencias.deleteById(c.jugador().getId());
        mvc.perform(get(API + "/mi-perfil/preferencias").header("Authorization",auth(c.usuarioJugador())))
            .andExpect(jsonPath("$.visible").value(false)).andExpect(jsonPath("$.aceptaSolicitudes").value(false));
        mvc.perform(put(API + "/mi-perfil/preferencias").header("Authorization",auth(c.usuarioJugador()))
            .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":true,\"aceptaSolicitudes\":true,\"version\":0}"))
            .andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(ints = {17,0})
    void bloqueaMenorYEdadDesconocida(int edad) throws Exception {
        var c = caso(); var j = jugadores.findById(c.jugador().getId()).orElseThrow();
        j.setFechaNacimiento(edad == 0 ? null : LocalDate.now(ZoneOffset.UTC).minusYears(edad)); jugadores.saveAndFlush(j);
        crear(c,UUID.randomUUID(),c.representante()).andExpect(status().isNotFound());
        mvc.perform(put(API + "/mi-perfil/preferencias").header("Authorization",auth(c.usuarioJugador()))
            .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":true,\"aceptaSolicitudes\":true,\"version\":0}"))
            .andExpect(status().isBadRequest());
    }
    @Test void bloqueaOptOutYJugadorInactivo() throws Exception {
        var c = caso(); var p = preferencias.findById(c.jugador().getId()).orElseThrow(); p.setAceptaSolicitudes(false); preferencias.saveAndFlush(p);
        crear(c,UUID.randomUUID(),c.representante()).andExpect(status().isNotFound());
        p = preferencias.findById(c.jugador().getId()).orElseThrow(); p.setAceptaSolicitudes(true); preferencias.saveAndFlush(p);
        var j = jugadores.findById(c.jugador().getId()).orElseThrow(); j.setEstado(EstadoJugador.INACTIVO); jugadores.saveAndFlush(j);
        crear(c,UUID.randomUUID(),c.representante()).andExpect(status().isNotFound());
    }
    @Test void noPuedeActuarPorOtroClubNiAceptarEnNombreDelJugador() throws Exception {
        var c = caso(); var otro = caso();
        crear(c,UUID.randomUUID(),otro.representante()).andExpect(status().isForbidden());
        var id = nueva(c); resolver(c,id,c.representante(),"ACEPTADA",0).andExpect(status().isForbidden());
    }
    @Test void solicitudIdempotenteConHistorialYCorreoPendienteSinFingirEnvio() throws Exception {
        var c = caso(); var key = UUID.randomUUID();
        var id = json.readTree(crear(c,key,c.representante()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        crear(c,key,c.representante()).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        var pendientes = correos.findByEventoSolicitudId(UUID.fromString(id));
        assertThat(pendientes).hasSize(2).allMatch(m -> m.getEstado().equals("PENDIENTE_CONFIGURACION"));
        assertThat(pendientes.stream().map(CorreoContacto::getDestinatarioId)).containsExactlyInAnyOrder(c.representante().getId(),c.usuarioJugador().getId());
        mvc.perform(get(API + "/solicitudes/" + id + "/historial").header("Authorization",auth(c.usuarioJugador())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void claveReutilizadaConOtroContenidoYDuplicadoPendienteSonConflictos() throws Exception {
        var c = caso(); var key = UUID.randomUUID(); crear(c,key,c.representante()).andExpect(status().isCreated());
        crear(c,UUID.randomUUID(),c.representante()).andExpect(status().isConflict());
        mvc.perform(post(API + "/solicitudes").header("Authorization",auth(c.representante())).header("Idempotency-Key",key)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("clubId",c.club().getId(),"jugadorId",c.jugador().getId(),"motivo","Otro motivo"))))
            .andExpect(status().isConflict());
    }
    @Test void aceptarHabilitaCorreoConAuditoriaYRevocarLoBloquea() throws Exception {
        var c = caso(); var id = nueva(c); datos(id,c.representante()).andExpect(status().isConflict());
        resolver(c,id,c.usuarioJugador(),"ACEPTADA",0).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        datos(id,c.representante()).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.correoJugador").value(c.usuarioJugador().getEmail()));
        assertThat(accesos.findAll().stream().filter(a -> a.getSolicitud().getId().equals(UUID.fromString(id))).count()).isEqualTo(1);
        var p = preferencias.findById(c.jugador().getId()).orElseThrow(); p.setAceptaSolicitudes(false); preferencias.saveAndFlush(p);
        datos(id,c.representante()).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings = {"ACEPTADA","RECHAZADA"})
    void respuestaEsFinalYRespetaVersion(String estado) throws Exception {
        var c = caso(); var id = nueva(c);
        resolver(c,id,c.usuarioJugador(),estado,7).andExpect(status().isConflict());
        resolver(c,id,c.usuarioJugador(),estado,0).andExpect(status().isOk());
        resolver(c,id,c.usuarioJugador(),estado,1).andExpect(status().isConflict());
        crear(c,UUID.randomUUID(),c.representante()).andExpect(status().isConflict());
    }
    @Test void soloClubPuedeCancelar() throws Exception {
        var c = caso(); var id = nueva(c);
        resolver(c,id,c.usuarioJugador(),"CANCELADA",0).andExpect(status().isForbidden());
        resolver(c,id,c.representante(),"CANCELADA",0).andExpect(status().isOk());
    }
    @Test void privadoInclusoConRolAdminYBandejasSeparadas() throws Exception {
        var c = caso(); var id = nueva(c); var ajeno = usuario();
        var admin = "Bearer " + jwt.emitir(ajeno.getId(),ajeno.getName(),List.of("ADMIN"),null);
        mvc.perform(get(API + "/solicitudes/" + id).header("Authorization",admin)).andExpect(status().isNotFound());
        mvc.perform(get(API + "/solicitudes").header("Authorization",auth(ajeno)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(API + "/solicitudes").header("Authorization",auth(c.usuarioJugador())))
            .andExpect(jsonPath("$.totalElements").value(1));
        datos(id,ajeno).andExpect(status().isNotFound());
    }
    @Test void revocarVinculoConClubRetiraAcceso() throws Exception {
        var c = caso(); var id = nueva(c);
        trabajos.deleteAll(trabajos.findByUsuarioClubUsuarioId(c.representante().getId()));
        mvc.perform(get(API + "/solicitudes/" + id).header("Authorization",auth(c.representante()))).andExpect(status().isNotFound());
    }
    @Test void guardadosSonDelClubEIdempotentes() throws Exception {
        var c = caso(); var path = API + "/clubes/" + c.club().getId() + "/guardados/" + c.jugador().getId();
        mvc.perform(put(path).header("Authorization",auth(c.representante()))).andExpect(status().isOk());
        mvc.perform(put(path).header("Authorization",auth(c.representante()))).andExpect(status().isOk());
        mvc.perform(get(API + "/clubes/" + c.club().getId() + "/guardados").header("Authorization",auth(c.representante())))
            .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(API + "/clubes/" + c.club().getId() + "/guardados").header("Authorization",auth(c.usuarioJugador())))
            .andExpect(status().isForbidden());
        mvc.perform(delete(path).header("Authorization",auth(c.representante()))).andExpect(status().isNoContent());
        mvc.perform(delete(path).header("Authorization",auth(c.representante()))).andExpect(status().isNoContent());
    }
    @Test void paginacionValidaYBusquedaEscapaComodines() throws Exception {
        var c = caso();
        mvc.perform(get(API + "/jugadores").param("q","%").header("Authorization",auth(c.representante())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(API + "/jugadores").param("size","101").header("Authorization",auth(c.representante()))).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/jugadores").param("page","-1").header("Authorization",auth(c.representante()))).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/clubes").param("q",c.club().getNombreClub()).header("Authorization",auth(c.usuarioJugador())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(c.club().getId()));
    }
    @Test void concurrenciaNoDuplicaSolicitudNiCorreos() throws Exception {
        var c = caso(); var key = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Integer> crear = () -> { inicio.await(); return crear(c,key,c.representante()).andReturn().getResponse().getStatus(); };
            var a = executor.submit(crear); var b = executor.submit(crear); inicio.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,200);
        }
        assertThat(solicitudes.findBySolicitanteIdAndClave(c.representante().getId(),key)).isPresent();
    }
    @Test void filtraJugadoresPorPosicionYFichajeVigente() throws Exception {
        var c = caso(); var pos = new Posicion(); pos.setPosicion("Arquero " + c.jugador().getId());
        pos.setCodigoPosicion("ARQ"); pos = posiciones.saveAndFlush(pos);
        var pj = new PosicionJugador(); pj.setJugador(c.jugador()); pj.setPosicion(pos); posicionesJugador.saveAndFlush(pj);
        var f = new Fichado(); f.setClub(c.club()); f.setJugador(c.jugador());
        f.setFechaFichaje(LocalDateTime.now(ZoneOffset.UTC).minusDays(1)); fichados.saveAndFlush(f);
        mvc.perform(get(API + "/jugadores").param("posicionId",pos.getId().toString()).param("clubId",c.club().getId().toString())
            .header("Authorization",auth(c.representante()))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        f.setFechaFinalFichaje(LocalDateTime.now(ZoneOffset.UTC).minusHours(1)); fichados.saveAndFlush(f);
        mvc.perform(get(API + "/jugadores").param("clubId",c.club().getId().toString()).header("Authorization",auth(c.representante())))
            .andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void rechazaCuerpoInvalidoClaveAusenteYTransicionPendiente() throws Exception {
        var c = caso();
        mvc.perform(post(API + "/solicitudes").header("Authorization",auth(c.representante()))
            .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(post(API + "/solicitudes").header("Authorization",auth(c.representante())).header("Idempotency-Key",UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
        var id = nueva(c); resolver(c,id,c.usuarioJugador(),"PENDIENTE",0).andExpect(status().isBadRequest());
    }
    @Test void permitePreflightDeFlutterWeb() throws Exception {
        mvc.perform(options(API + "/solicitudes").header("Origin","http://localhost:3000")
            .header("Access-Control-Request-Method","POST")
            .header("Access-Control-Request-Headers","authorization,content-type,idempotency-key"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:3000"));
    }
    @Test void dosClavesDistintasNoCreanDosPendientes() throws Exception {
        var c = caso();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Integer> crear = () -> { inicio.await(); return crear(c,UUID.randomUUID(),c.representante()).andReturn().getResponse().getStatus(); };
            var a = executor.submit(crear); var b = executor.submit(crear); inicio.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
    }
    @Test void retiroConsentimientoImpideAceptarYFiltroDeEstadoFunciona() throws Exception {
        var c = caso(); var id = nueva(c);
        var p = preferencias.findById(c.jugador().getId()).orElseThrow(); p.setAceptaSolicitudes(false); preferencias.saveAndFlush(p);
        resolver(c,id,c.usuarioJugador(),"ACEPTADA",0).andExpect(status().isBadRequest());
        resolver(c,id,c.usuarioJugador(),"RECHAZADA",0).andExpect(status().isOk());
        mvc.perform(get(API + "/solicitudes").param("estado","ACEPTADA").header("Authorization",auth(c.usuarioJugador())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(API + "/solicitudes").param("estado","RECHAZADA").header("Authorization",auth(c.usuarioJugador())))
            .andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void preparaCorreoSinCcSinDatosSensiblesNiEnvioYRespetaRevocacion() throws Exception {
        var c = caso(); var id = nueva(c);
        var correo = correos.findByEventoSolicitudId(UUID.fromString(id)).stream()
            .filter(m -> m.getPlantilla().equals("INVITACION_JUGADOR")).findFirst().orElseThrow();
        var preparado = preparador.preparar(correo.getId());
        assertThat(preparado.de()).isEqualTo("contacto@semillero.example.test");
        assertThat(preparado.para()).isEqualTo(c.usuarioJugador().getEmail());
        assertThat(preparado.responderA()).isEqualTo(preparado.de());
        assertThat(preparado.texto()).contains("https://semillero.example.test/contactos/solicitudes/" + id)
            .doesNotContain(c.representante().getEmail(),"documento-privado","Invitación a prueba deportiva");
        assertThat(correos.findById(correo.getId()).orElseThrow().getEstado()).isEqualTo("PENDIENTE_CONFIGURACION");
        var p = preferencias.findById(c.jugador().getId()).orElseThrow(); p.setAceptaSolicitudes(false); preferencias.saveAndFlush(p);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> preparador.preparar(correo.getId()))
            .isInstanceOf(edu.upb.semillero.service.exception.ConflictException.class);
    }
    @Test void openApiIncluyeContratoNuevo() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/scouting/solicitudes']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/scouting/mi-perfil/preferencias']").exists());
    }
}
