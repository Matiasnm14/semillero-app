package edu.upb.semillero.service.scouting;

import java.time.Clock;
import org.springframework.context.annotation.*;
@Configuration
public class ScoutingConfiguration {
    @Bean("scoutingClock") Clock scoutingClock() { return Clock.systemUTC(); }
}
