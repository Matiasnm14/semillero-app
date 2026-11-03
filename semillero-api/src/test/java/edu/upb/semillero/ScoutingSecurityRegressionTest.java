package edu.upb.semillero;

import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.service.seguridad.JwtService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regression tests for the shared API boundaries used by scouting. Synthetic H2 only. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScoutingSecurityRegressionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository users;
    @Autowired JugadorRepository players;
    @Autowired ClubRepository clubs;
    @Autowired UsuarioClubRepository staff;
    @Autowired UsuarioTrabajaRepository jobs;
    @Autowired FichadoRepository signings;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    private Usuario user() {
        Usuario u = new Usuario();
        u.setName(UUID.randomUUID().toString());
        u.setEmail(u.getName() + "@example.test");
        u.setNombres("Audit"); u.setApellidos("Synthetic");
        u.setTelefonoUsuario("70000000"); u.setDocIdentidadUsuario("SYNTHETIC-PRIVATE");
        u.setPasswordHash(encoder.encode("OriginalTest123!"));
        return users.saveAndFlush(u);
    }
    private Club club() {
        Club c = new Club(); c.setNombreClub("Audit " + UUID.randomUUID().toString().substring(0, 8));
        return clubs.saveAndFlush(c);
    }
    private Jugador player(Usuario u) {
        Jugador j = new Jugador(); j.setUsuario(u); j.setFechaNacimiento(LocalDate.of(2000,1,1));
        return players.saveAndFlush(j);
    }
    private String token(Usuario u, Long clubId) {
        return "Bearer " + jwt.emitir(u.getId(), u.getName(), List.of("CONSULTA"), clubId);
    }

    @Test void anonymousCannotReadPrivateUserFields() throws Exception {
        Usuario u = user();
        mvc.perform(get("/api/v1/usuarios/" + u.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/usuarios/" + u.getId()).header("Authorization",token(user(),null)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/usuarios/" + u.getId()).header("Authorization",token(u,null)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(u.getEmail()));
    }

    @Test void anonymousCannotCreateMembershipForAnyClub() throws Exception {
        Usuario u = user(); Club c = club();
        mvc.perform(post("/api/v1/usuarios-club").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("usuarioId",u.getId()))))
            .andExpect(status().isUnauthorized());
        UsuarioClub uc = new UsuarioClub(); uc.setUsuario(u); uc = staff.saveAndFlush(uc);
        String body = json.writeValueAsString(Map.of("usuarioClubId",uc.getId(),"clubId",c.getId(),"tipoUsuarioClub","EN"));
        mvc.perform(post("/api/v1/usuarios-trabaja").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/usuarios-trabaja").header("Authorization",token(u,null))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        assertThat(jobs.findByUsuarioClubUsuarioId(u.getId())).isEmpty();
    }

    @Test void clubStaffCannotReplacePlayerPassword() throws Exception {
        Usuario owner = user(); Usuario representative = user(); Club c = club(); Jugador j = player(owner);
        UsuarioClub uc = new UsuarioClub(); uc.setUsuario(representative); uc = staff.saveAndFlush(uc);
        UsuarioTrabaja job = new UsuarioTrabaja(); job.setUsuarioClub(uc); job.setClub(c); job.setTipoUsuarioClub("DI"); jobs.saveAndFlush(job);
        Fichado f = new Fichado(); f.setJugador(j); f.setClub(c); signings.saveAndFlush(f);
        mvc.perform(put("/api/v1/usuarios/" + owner.getId()).header("Authorization",token(representative,c.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                    "name",owner.getName(),"email",owner.getEmail(),"password","ReplacedTest123!"))))
            .andExpect(status().isForbidden());
        assertThat(encoder.matches("OriginalTest123!", users.findById(owner.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void playerCannotReassignOwnProfileToAnotherAccount() throws Exception {
        Usuario owner = user(); Usuario other = user(); Jugador j = player(owner);
        mvc.perform(put("/api/v1/jugadores/" + j.getId()).header("Authorization",token(owner,null))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("usuarioId",other.getId()))))
            .andExpect(status().isForbidden());
        assertThat(players.findFirstByUsuarioId(owner.getId())).isPresent();
        assertThat(players.findFirstByUsuarioId(other.getId())).isEmpty();
    }

    @Test void anonymousCannotChangeClubProfile() throws Exception {
        Club c = club();
        mvc.perform(put("/api/v1/clubes/" + c.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreClub\":\"Changed without session\"}"))
            .andExpect(status().isUnauthorized());
        assertThat(clubs.findById(c.getId()).orElseThrow().getNombreClub()).isEqualTo(c.getNombreClub());
    }

    @Test void anonymousCannotDeletePlayerProfile() throws Exception {
        Jugador j = player(user());
        mvc.perform(delete("/api/v1/jugadores/" + j.getId())).andExpect(status().isUnauthorized());
        assertThat(players.existsById(j.getId())).isTrue();
    }
    @Test void clubProfileRequiresCurrentMembershipAndAllowsItsRepresentative() throws Exception {
        Usuario u = user(); Club c = club();
        UsuarioClub uc = new UsuarioClub(); uc.setUsuario(u); uc = staff.saveAndFlush(uc);
        UsuarioTrabaja job = new UsuarioTrabaja(); job.setUsuarioClub(uc); job.setClub(c); job.setTipoUsuarioClub("DI"); job = jobs.saveAndFlush(job);
        String bearer = token(u,c.getId());
        mvc.perform(put("/api/v1/clubes/" + c.getId()).header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombreClub\":\"Updated by owner\"}"))
            .andExpect(status().isOk());
        jobs.deleteById(job.getId()); jobs.flush();
        mvc.perform(put("/api/v1/clubes/" + c.getId()).header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombreClub\":\"Revoked\"}"))
            .andExpect(status().isForbidden());
    }

    @Test void playerCannotChangeAgeToBypassScoutingSafeguards() throws Exception {
        Usuario owner = user(); Jugador j = player(owner);
        j.setFechaNacimiento(LocalDate.now().minusYears(16)); j = players.saveAndFlush(j);
        mvc.perform(put("/api/v1/jugadores/"+j.getId()).header("Authorization",token(owner,null))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "fechaNacimiento",LocalDate.now().minusYears(20).toString()))))
            .andExpect(status().isForbidden());
        assertThat(players.findById(j.getId()).orElseThrow().getFechaNacimiento())
            .isEqualTo(LocalDate.now().minusYears(16));
    }
    @Test void ownSportsUpdatePreservesIdentityAndBirthDateWhenOmitted() throws Exception {
        Usuario owner = user(); Jugador j = player(owner);
        mvc.perform(put("/api/v1/jugadores/"+j.getId()).header("Authorization",token(owner,null))
                .contentType(MediaType.APPLICATION_JSON).content("{\"altura\":1.8,\"peso\":75}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(owner.getId()))
            .andExpect(jsonPath("$.fechaNacimiento").value(j.getFechaNacimiento().toString()));
        mvc.perform(get("/api/v1/jugadores/"+j.getId()).header("Authorization",token(owner,null)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.altura").value(1.8));
    }

}
