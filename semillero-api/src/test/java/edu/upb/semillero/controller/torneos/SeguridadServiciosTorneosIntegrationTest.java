package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.dto.request.DocumentoJugadorRequestDto;
import edu.upb.semillero.repository.entity.DocumentoJugador;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.enums.EstadoDocumentoJugador;
import edu.upb.semillero.repository.enums.TipoDocumentoJugador;
import edu.upb.semillero.repository.repos.DocumentoJugadorRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.service.DocumentoJugadorService;
import edu.upb.semillero.service.exception.ForbiddenException;
import edu.upb.semillero.service.seguridad.AccesoTorneos;
import edu.upb.semillero.service.seguridad.JwtService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Regresiones de autorización, auditoría y vigencia de fichajes tras integrar Torneos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadServiciosTorneosIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired UsuarioRepository usuarios;
    @Autowired JugadorRepository jugadores;
    @Autowired DocumentoJugadorRepository documentos;
    @Autowired DocumentoJugadorService service;
    @Autowired AccesoTorneos acceso;
    @Autowired edu.upb.semillero.repository.repos.ClubRepository clubes;
    @Autowired edu.upb.semillero.repository.repos.UsuarioClubRepository usuariosClub;
    @Autowired edu.upb.semillero.repository.repos.UsuarioTrabajaRepository trabajos;
    @Autowired edu.upb.semillero.repository.repos.FichadoRepository fichados;

    @AfterEach void limpiar() { TestSecurityContextHolder.clearContext(); }

    private record Fixture(EscenarioTorneo torneo, long otro, long reglamento, String token) {}
    private Fixture prepararReglamento() throws Exception {
        TestSecurityContextHolder.setAuthentication(new UsernamePasswordAuthenticationToken(
                "fixture", null, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZADOR"))));
        EscenarioTorneo torneo=EscenarioTorneo.liga(mvc,json,0,0);
        var other=new Usuario();other.setName("review-other-"+System.nanoTime());
        other=usuarios.saveAndFlush(other);
        long reglamento=json.readTree(mvc.perform(get("/api/v1/torneos/"+torneo.torneoId+"/reglamento"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String token=jwt.emitir(torneo.usuarioId,"review-actor",List.of("ORGANIZADOR"),null);
        TestSecurityContextHolder.clearContext();
        return new Fixture(torneo,other.getId(),reglamento,token);
    }

    @Test
    void borrarReglamentoNoDebeAuditarAlUsuarioDeOtro() throws Exception {
        Fixture f=prepararReglamento();
        var response=mvc.perform(delete("/api/v1/torneos/"+f.torneo.torneoId+"/reglamento")
                .param("usuarioId",Long.toString(f.otro))
                .header("Authorization","Bearer "+f.token)).andReturn().getResponse();
        int attributed=jdbc.queryForObject("select count(*) from auditoria where tabla='reglamentos' and registro_id=? and accion='ELIMINAR' and users_id=?",Integer.class,f.reglamento,f.otro);
        assertThat(response.getStatus()).as("JWT A=%s; parametro B=%s; auditorias atribuidas a B=%s",f.torneo.usuarioId,f.otro,attributed).isEqualTo(403);
        assertThat(attributed).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reglamentos where id=?", Integer.class, f.reglamento)).isEqualTo(1);
    }

    @Test
    void reemplazarDesempatesNoDebeAuditarAlUsuarioDeOtro() throws Exception {
        Fixture f=prepararReglamento();
        var response=mvc.perform(put("/api/v1/torneos/"+f.torneo.torneoId+"/reglamento/desempates")
                .header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("codigos",List.of("GOLES_FAVOR","DIF_GOLES"),"usuarioId",f.otro))))
                .andReturn().getResponse();
        int attributed=jdbc.queryForObject("select count(*) from auditoria where tabla='reglamentos' and registro_id=? and accion='MODIFICAR_DESEMPATES' and users_id=?",Integer.class,f.reglamento,f.otro);
        assertThat(response.getStatus()).as("JWT A=%s; cuerpo B=%s; auditorias atribuidas a B=%s",f.torneo.usuarioId,f.otro,attributed).isEqualTo(403);
        assertThat(attributed).isZero();
    }

    @Test
    void elServicioNoDebeCrearAptoVigenteSinAutenticacion() {
        SecurityContextHolder.clearContext();
        Jugador p=jugadores.saveAndFlush(new Jugador());
        var body=new DocumentoJugadorRequestDto(p.getId(),TipoDocumentoJugador.APTO_MEDICO,null,null,null,EstadoDocumentoJugador.VIGENTE);
        assertThatThrownBy(()->service.create(body)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void elServicioNoDebeListarDocumentosSinAutenticacion() {
        SecurityContextHolder.clearContext();
        Jugador p=jugadores.saveAndFlush(new Jugador());
        DocumentoJugador d=new DocumentoJugador();d.setJugador(p);d.setTipo(TipoDocumentoJugador.APTO_MEDICO);d.setEstado(EstadoDocumentoJugador.VIGENTE);
        documentos.saveAndFlush(d);
        assertThatThrownBy(()->service.findAll(org.springframework.data.domain.PageRequest.of(0,10)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sinPrincipalNoDebeTomarElActorDelCuerpo() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(()->acceso.exigirActor(42L,"revision-local"))
                .isInstanceOf(ForbiddenException.class);
    }

    private record DocumentoPersonal(long id, String token) {}
    private DocumentoPersonal documentoParaPersonal(java.time.LocalDateTime inicio, java.time.LocalDateTime fin) {
        SecurityContextHolder.clearContext();
        var actor=new Usuario();actor.setName("personal-review-"+System.nanoTime());actor=usuarios.saveAndFlush(actor);
        var club=new edu.upb.semillero.repository.entity.Club();club.setNombreClub("Club review "+System.nanoTime());club=clubes.saveAndFlush(club);
        var cuenta=new edu.upb.semillero.repository.entity.UsuarioClub();cuenta.setUsuario(actor);cuenta=usuariosClub.saveAndFlush(cuenta);
        var trabajo=new edu.upb.semillero.repository.entity.UsuarioTrabaja();trabajo.setUsuarioClub(cuenta);trabajo.setClub(club);trabajo.setTipoUsuarioClub("EN");trabajos.saveAndFlush(trabajo);
        Jugador p=jugadores.saveAndFlush(new Jugador());
        var fichaje=new edu.upb.semillero.repository.entity.Fichado();fichaje.setClub(club);fichaje.setJugador(p);fichaje.setFechaFichaje(inicio);fichaje.setFechaFinalFichaje(fin);fichados.saveAndFlush(fichaje);
        var doc=new DocumentoJugador();doc.setJugador(p);doc.setTipo(TipoDocumentoJugador.APTO_MEDICO);doc.setEstado(EstadoDocumentoJugador.VIGENTE);doc=documentos.saveAndFlush(doc);
        return new DocumentoPersonal(doc.getId(),jwt.emitir(actor.getId(),actor.getName(),List.of("ENTRENADOR"),club.getId()));
    }

    @Test
    void unFichajeVigenteConFechaFinalFuturaDebePermitirAlPersonalLeerElDocumento() throws Exception {
        var now=java.time.LocalDateTime.now();
        var f=documentoParaPersonal(now.minusDays(10),now.plusDays(30));
        int status=mvc.perform(get("/api/v1/documentos-jugador/"+f.id)
                .header("Authorization","Bearer "+f.token)).andReturn().getResponse().getStatus();
        assertThat(status).as("El contrato está vigente y termina en 30 días").isEqualTo(200);
        verificarListado(f, 1);
    }

    @Test
    void unFichajeQueEmpiezaEnElFuturoNoDebePermitirLeerDocumentosHoy() throws Exception {
        var f=documentoParaPersonal(java.time.LocalDateTime.now().plusDays(30),null);
        int status=mvc.perform(get("/api/v1/documentos-jugador/"+f.id)
                .header("Authorization","Bearer "+f.token)).andReturn().getResponse().getStatus();
        assertThat(status).as("El contrato recién comienza en 30 días").isEqualTo(403);
        verificarListado(f, 0);
    }

    private void verificarListado(DocumentoPersonal f, int cantidad) throws Exception {
        String body = mvc.perform(get("/api/v1/documentos-jugador")
                        .header("Authorization", "Bearer " + f.token))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("content").size()).isEqualTo(cantidad);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"-10,-1,403", "-10,0,200", "0,30,200", "30,60,403"})
    void laVigenciaSeAplicaIgualAlDetalleYAlListado(int inicio, int fin, int esperado) throws Exception {
        var hoy = java.time.LocalDate.now();
        var f = documentoParaPersonal(hoy.plusDays(inicio).atStartOfDay(), hoy.plusDays(fin).atStartOfDay());
        int status = mvc.perform(get("/api/v1/documentos-jugador/" + f.id)
                        .header("Authorization", "Bearer " + f.token))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(esperado);
        verificarListado(f, esperado == 200 ? 1 : 0);
    }

    @Test
    void elReglamentoRegistraAlActorRealEnLasOperacionesPermitidas() throws Exception {
        Fixture f = prepararReglamento();
        String path = "/api/v1/torneos/" + f.torneo.torneoId + "/reglamento";
        int changed = mvc.perform(put(path + "/desempates")
                        .header("Authorization", "Bearer " + f.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("codigos", List.of("GOLES_FAVOR", "DIF_GOLES"),
                                "usuarioId", f.torneo.usuarioId))))
                .andReturn().getResponse().getStatus();
        assertThat(changed).isEqualTo(200);
        int deleted = mvc.perform(delete(path).param("usuarioId", Long.toString(f.torneo.usuarioId))
                        .header("Authorization", "Bearer " + f.token))
                .andReturn().getResponse().getStatus();
        assertThat(deleted).isEqualTo(204);
        assertThat(jdbc.queryForObject("""
                select count(*) from auditoria where tabla='reglamentos' and registro_id=?
                and accion in ('ELIMINAR','MODIFICAR_DESEMPATES') and users_id=?
                """, Integer.class, f.reglamento, f.torneo.usuarioId)).isEqualTo(2);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ausente", "anonimo", "anonimo-tipado", "generico", "no-autenticado", "sin-id"})
    void unaIdentidadInvalidaNoOtorgaPermisosNiAutorizaUnActor(String tipo) {
        SecurityContextHolder.clearContext();
        var permisos = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        org.springframework.security.core.Authentication auth = switch (tipo) {
            case "ausente" -> null;
            case "anonimo" -> new org.springframework.security.authentication.AnonymousAuthenticationToken(
                    "fixture", "anonymousUser", permisos);
            case "anonimo-tipado" -> new org.springframework.security.authentication.AnonymousAuthenticationToken(
                    "fixture", new edu.upb.semillero.service.seguridad.UsuarioAutenticado(1L, "fixture", List.of("ADMIN"), null), permisos);
            case "generico" -> UsernamePasswordAuthenticationToken.authenticated("fixture", null, permisos);
            case "no-autenticado" -> UsernamePasswordAuthenticationToken.unauthenticated(
                    new edu.upb.semillero.service.seguridad.UsuarioAutenticado(1L, "fixture", List.of("ADMIN"), null), null);
            default -> UsernamePasswordAuthenticationToken.authenticated(
                    new edu.upb.semillero.service.seguridad.UsuarioAutenticado(null, "fixture", List.of("ADMIN"), null), null, permisos);
        };
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThatThrownBy(() -> acceso.exigirActor(42L, "regresion-local"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.findAll(org.springframework.data.domain.PageRequest.of(0, 10)))
                .isInstanceOf(ForbiddenException.class);
    }
}
