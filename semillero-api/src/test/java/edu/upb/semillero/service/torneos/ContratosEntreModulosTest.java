package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.enums.EstadoJugador;
import edu.upb.semillero.service.comun.ComunService;
import edu.upb.semillero.service.fichatecnica.FichaTecnicaService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Puntos 10, 11 y 12 del acuerdo de la S1-04: las interfaces entre módulos
 * existen, se pueden inyectar, y los stubs responden lo que su documentación
 * dice que responden.
 *
 * <p>Esta prueba también es el aviso de cuándo borrar los stubs: el día que
 * ficha técnica agregue sus implementaciones reales, los casos que comprueban
 * el comportamiento del stub van a fallar, y eso es lo que se espera.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContratosEntreModulosTest {
    @Autowired FichaTecnicaService fichaTecnica;
    @Autowired ComunService comun;

    @Test
    void lasInterfacesEntreModulosSeInyectan() {
        assertThat(fichaTecnica).isNotNull();
        assertThat(comun).isNotNull();
    }

    @Test
    void elStubDeFichaTecnicaRespondeUnJugadorActivoYConDocumentos() {
        var datos = fichaTecnica.datosParaTorneos(45L, LocalDate.of(2026, 12, 31));
        assertThat(datos).isPresent();
        assertThat(datos.get().jugadorId()).isEqualTo(45L);
        assertThat(datos.get().estado()).isEqualTo(EstadoJugador.ACTIVO);
        assertThat(datos.get().documentosVigentes()).isTrue();
        assertThat(datos.get().aptoMedicoVigente()).isTrue();
    }

    /**
     * Lo que el stub NO puede saber. Mientras esto siga vacío, las validaciones
     * de la lista de buena fe que dependen del fichaje no están cubiertas.
     */
    @Test
    void elStubDeFichaTecnicaNoSabeNadaDelFichaje() {
        assertThat(fichaTecnica.datosParaTorneos(45L, LocalDate.of(2026, 12, 31)).orElseThrow()
                .clubesDondeEstaFichado()).isEmpty();
        assertThat(fichaTecnica.jugadoresFichadosEn(3L, LocalDate.of(2026, 10, 26))).isEmpty();
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
}
