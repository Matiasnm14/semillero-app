package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Rol;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.UsuarioRol;
import edu.upb.semillero.repository.entity.Fichado;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.enums.EstadoJugador;
import edu.upb.semillero.repository.repos.ClubRepository;
import edu.upb.semillero.repository.repos.FichadoRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.RolRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.repository.repos.UsuarioRolRepository;
import edu.upb.semillero.service.comun.ComunService;
import edu.upb.semillero.service.comun.Roles;
import edu.upb.semillero.service.fichatecnica.FichaTecnicaService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Puntos 10, 11 y 12 del acuerdo de la S1-04: las interfaces entre módulos
 * existen, se pueden inyectar y responden lo que su documentación dice.
 *
 * <p>Tarea S2-05: {@code FichaTecnicaService} ya no inventa los datos, los lee
 * de {@code jugadores} y {@code fichado}. Lo único que sigue falseado son los
 * documentos y el apto médico, porque {@code documentos_jugador} es de ficha
 * técnica y todavía no existe.
 *
 * <p>Esta prueba también es el aviso de cuándo borrar
 * {@code MocksExternosConfiguration}: el día que ficha técnica agregue sus
 * implementaciones reales, los casos que comprueban el comportamiento del stub
 * de {@code ComunService} van a fallar, y eso es lo que se espera.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContratosEntreModulosTest {
    private static final LocalDate CORTE = LocalDate.of(2026, 12, 31);

    @Autowired FichaTecnicaService fichaTecnica;
    @Autowired ComunService comun;
    @Autowired JugadorRepository jugadores;
    @Autowired ClubRepository clubes;
    @Autowired FichadoRepository fichados;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired UsuarioRolRepository usuariosRoles;

    @Test
    void lasInterfacesEntreModulosSeInyectan() {
        assertThat(fichaTecnica).isNotNull();
        assertThat(comun).isNotNull();
    }

    @Test
    void devuelveVacioSiElJugadorNoExiste() {
        assertThat(fichaTecnica.datosParaTorneos(Long.MAX_VALUE, CORTE)).isEmpty();
        assertThat(fichaTecnica.datosParaTorneos(null, CORTE)).isEmpty();
    }

    @Test
    void calculaLaEdadALaFechaDeCorteYLeeElEstado() {
        Jugador jugador = jugador(LocalDate.of(2012, 6, 1));

        var datos = fichaTecnica.datosParaTorneos(jugador.getId(), CORTE).orElseThrow();

        assertThat(datos.jugadorId()).isEqualTo(jugador.getId());
        assertThat(datos.edadAFechaDeCorte()).isEqualTo(14);
        assertThat(datos.estado()).isEqualTo(EstadoJugador.ACTIVO);
    }

    /** La fecha de corte manda: al 31/12 del año anterior el mismo jugador tiene un año menos. */
    @Test
    void laEdadDependeDeLaFechaDeCorteYNoDeHoy() {
        Jugador jugador = jugador(LocalDate.of(2012, 6, 1));

        assertThat(fichaTecnica.datosParaTorneos(jugador.getId(), LocalDate.of(2025, 12, 31))
                .orElseThrow().edadAFechaDeCorte()).isEqualTo(13);
    }

    /** Sin fecha de nacimiento no hay edad: se devuelve 0 y el que valida decide. */
    @Test
    void devuelveEdadDesconocidaSiElJugadorNoTieneFechaDeNacimiento() {
        Jugador jugador = jugador(null);

        assertThat(fichaTecnica.datosParaTorneos(jugador.getId(), CORTE).orElseThrow()
                .edadAFechaDeCorte()).isEqualTo(MocksExternosConfiguration.EDAD_DESCONOCIDA);
    }

    @Test
    void leeElFichajeVigenteDeLaTablaFichado() {
        Jugador jugador = jugador(LocalDate.of(2012, 6, 1));
        Club club = club("Club con fichaje");
        fichaje(jugador, club, LocalDateTime.of(2026, 1, 15, 0, 0), null);

        var datos = fichaTecnica.datosParaTorneos(jugador.getId(), CORTE).orElseThrow();

        assertThat(datos.clubesDondeEstaFichado()).containsExactly(club.getId());
        assertThat(fichaTecnica.jugadoresFichadosEn(club.getId(), CORTE)).contains(jugador.getId());
    }

    @Test
    void noCuentaUnFichajeQueYaTerminoALaFechaDeCorte() {
        Jugador jugador = jugador(LocalDate.of(2012, 6, 1));
        Club club = club("Club con fichaje vencido");
        fichaje(jugador, club, LocalDateTime.of(2025, 1, 15, 0, 0), LocalDateTime.of(2026, 6, 30, 0, 0));

        assertThat(fichaTecnica.datosParaTorneos(jugador.getId(), CORTE).orElseThrow()
                .clubesDondeEstaFichado()).isEmpty();
        assertThat(fichaTecnica.jugadoresFichadosEn(club.getId(), CORTE)).isEmpty();
    }

    /**
     * Los documentos y el apto médico siguen sin verificarse. Mientras esta
     * prueba pase, esas dos validaciones de la HU 5 no comprueban nada.
     */
    @Test
    void losDocumentosYElAptoMedicoTodaviaNoSeVerifican() {
        Jugador jugador = jugador(LocalDate.of(2012, 6, 1));

        var datos = fichaTecnica.datosParaTorneos(jugador.getId(), CORTE).orElseThrow();

        assertThat(datos.documentosVigentes()).isTrue();
        assertThat(datos.aptoMedicoVigente()).isTrue();
    }

    /**
     * Tarea S2-02: {@code ComunService} ya no es un stub. Sin roles cargados, un
     * usuario cualquiera no tiene ninguno y queda como CONSULTA: solo lectura.
     */
    @Test
    void comunNoConcedeRolesQueElUsuarioNoTiene() {
        assertThat(comun.tieneRol(1L, Roles.ORGANIZADOR)).isFalse();
        assertThat(comun.tieneRol(1L, "ROL_QUE_NO_EXISTE")).isFalse();
        assertThat(comun.tieneRol(null, Roles.ADMIN)).isFalse();
        assertThat(comun.clubesDondeTrabaja(1L)).isEmpty();
    }

    @Test
    void comunDaElRolQueEstaEnUsuariosRoles() {
        Usuario usuario = usuario();
        Rol organizador = roles.findByCodigo(Roles.ORGANIZADOR).orElseThrow();
        UsuarioRol asignacion = new UsuarioRol();
        asignacion.setUsuario(usuario);
        asignacion.setRol(organizador);
        asignacion.setFechaAlta(LocalDateTime.now());
        usuariosRoles.saveAndFlush(asignacion);

        assertThat(comun.tieneRol(usuario.getId(), Roles.ORGANIZADOR)).isTrue();
        assertThat(comun.rolesDe(usuario.getId())).contains(Roles.ORGANIZADOR);
    }

    /** Sin ningún rol asignado, el usuario solo puede consultar. */
    @Test
    void sinRolesAsignadosElUsuarioQuedaEnConsulta() {
        assertThat(comun.rolesDe(usuario().getId())).containsExactly(Roles.CONSULTA);
    }

    @Test
    void comunValidaQueLosIdsNoSeanNulos() {
        Usuario usuario = usuario();
        assertThat(comun.usuarioExiste(usuario.getId())).isTrue();
        assertThat(comun.usuarioExiste(null)).isFalse();
        assertThat(comun.usuarioExiste(Long.MAX_VALUE)).isFalse();
        assertThat(comun.clubExiste(club("Club comun").getId())).isTrue();
        assertThat(comun.clubExiste(null)).isFalse();
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setName("u" + System.nanoTime());
        usuario.setNombres("Test");
        usuario.setApellidos("Test");
        usuario.setEmail("contratos" + System.nanoTime() + "@example.com");
        usuario.setPasswordHash("x");
        return usuarios.saveAndFlush(usuario);
    }

    private Jugador jugador(LocalDate fechaNacimiento) {
        Jugador jugador = new Jugador();
        jugador.setFechaNacimiento(fechaNacimiento);
        return jugadores.saveAndFlush(jugador);
    }

    private Club club(String nombre) {
        Club club = new Club();
        club.setNombreClub(nombre);
        return clubes.saveAndFlush(club);
    }

    private void fichaje(Jugador jugador, Club club, LocalDateTime desde, LocalDateTime hasta) {
        Fichado fichado = new Fichado();
        fichado.setJugador(jugador);
        fichado.setClub(club);
        fichado.setFechaFichaje(desde);
        fichado.setFechaFinalFichaje(hasta);
        fichados.saveAndFlush(fichado);
    }
}
