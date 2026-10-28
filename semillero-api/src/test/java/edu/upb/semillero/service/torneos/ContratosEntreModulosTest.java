package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Fichado;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.enums.EstadoJugador;
import edu.upb.semillero.repository.repos.ClubRepository;
import edu.upb.semillero.repository.repos.FichadoRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.service.comun.ComunService;
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
     * El stub concede cualquier rol: hasta que exista la implementación real con
     * `roles` y `usuarios_roles`, la HU 16 no tiene autorización de verdad.
     */
    @Test
    void elStubDeComunConcedeCualquierRol() {
        assertThat(comun.tieneRol(1L, "ORGANIZADOR")).isTrue();
        assertThat(comun.tieneRol(1L, "ROL_QUE_NO_EXISTE")).isTrue();
        assertThat(comun.clubesDondeTrabaja(1L)).isEmpty();
    }

    @Test
    void elStubDeComunValidaQueLosIdsNoSeanNulos() {
        assertThat(comun.usuarioExiste(1L)).isTrue();
        assertThat(comun.usuarioExiste(null)).isFalse();
        assertThat(comun.clubExiste(1L)).isTrue();
        assertThat(comun.clubExiste(null)).isFalse();
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
