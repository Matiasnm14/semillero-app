package edu.upb.semillero.config;

import edu.upb.semillero.repository.entity.Ciudad;
import edu.upb.semillero.repository.entity.Pais;
import edu.upb.semillero.repository.entity.Posicion;
import edu.upb.semillero.repository.repos.CiudadRepository;
import edu.upb.semillero.repository.repos.PaisRepository;
import edu.upb.semillero.repository.repos.PosicionRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Datos mínimos para poder probar el registro local sin carga manual previa. */
@Configuration
@Profile("dev")
public class DevelopmentDataInitializer {
    @Bean
    CommandLineRunner localSeed(PaisRepository paises, CiudadRepository ciudades, PosicionRepository posiciones) {
        return args -> {
            Pais bolivia = paises.findAll().stream().findFirst().orElseGet(() -> {
                Pais pais = new Pais();
                pais.setNombrePais("Bolivia");
                return paises.save(pais);
            });
            if (ciudades.count() == 0) {
                for (String nombre : List.of("La Paz", "Santa Cruz de la Sierra", "Cochabamba", "Sucre")) {
                    Ciudad ciudad = new Ciudad();
                    ciudad.setNombreCiudad(nombre);
                    ciudad.setPais(bolivia);
                    ciudades.save(ciudad);
                }
            }
            if (posiciones.count() == 0) {
                for (String[] item : List.of(
                        new String[]{"Portero", "POR"}, new String[]{"Portero Cierre", "PCI"},
                        new String[]{"Lateral", "LAT"}, new String[]{"Carrilero", "CAR"},
                        new String[]{"Lateral limitado", "LTL"}, new String[]{"Carrilero completo", "CRC"},
                        new String[]{"Carrilero inverso", "CIN"}, new String[]{"Defensa Central", "DFC"},
                        new String[]{"Defensa con toque", "DCT"}, new String[]{"Defensa conservador", "DCN"},
                        new String[]{"Centrocampista de banda", "INT"}, new String[]{"Extremo", "EXT"},
                        new String[]{"Interior defensivo", "IDF"}, new String[]{"Organizador de banda", "OES"},
                        new String[]{"Centrocampista", "CM"}, new String[]{"Pivote organizador", "PVO"},
                        new String[]{"Centrocampista todoterreno", "CTT"}, new String[]{"Organizador adelantado", "OAD"},
                        new String[]{"Centrocampista recuperador", "CRP"}, new String[]{"Organizador itinerante", "OIT"},
                        new String[]{"Segundo delantero", "2ºD"}, new String[]{"Delantero avanzado", "DLA"},
                        new String[]{"Hombre objetivo", "OBJ"}, new String[]{"Ariete", "ARI"},
                        new String[]{"Delantero completo", "DLC"}, new String[]{"Delantero defensivo", "DLD"},
                        new String[]{"Trequartista", "TRE"}, new String[]{"Falso nueve", "FN"},
                        new String[]{"Cierre", "CIE"}, new String[]{"Libero", "LIB"},
                        new String[]{"Mediocentro", "MC"}, new String[]{"Pivote defensivo", "PVD"},
                        new String[]{"Medio cierre", "MCI"}, new String[]{"Regista", "REG"},
                        new String[]{"Delantero interior", "DI"}, new String[]{"Hombre objetivo escorado", "HOE"},
                        new String[]{"Buscador de espacios", "BDE"}, new String[]{"Mediapunta", "MP"},
                        new String[]{"Enganche", "ENG"}, new String[]{"Delantero sorpresa", "DLS"})) {
                    Posicion posicion = new Posicion();
                    posicion.setPosicion(item[0]);
                    posicion.setCodigoPosicion(item[1]);
                    posiciones.save(posicion);
                }
            }
        };
    }
}
