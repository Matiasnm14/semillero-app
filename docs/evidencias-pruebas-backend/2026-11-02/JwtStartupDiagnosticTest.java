package edu.upb.semillero.config;

import edu.upb.semillero.service.seguridad.JwtService;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

class JwtStartupDiagnosticTest {
    private AnnotationConfigApplicationContext context(boolean vps, boolean withSecret) throws Exception {
        var ctx=new AnnotationConfigApplicationContext();
        var sources=ctx.getEnvironment().getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        var base=new Properties();
        try(var input=new ClassPathResource("application.properties").getInputStream()){base.load(input);}
        sources.addLast(new PropertiesPropertySource("base",base));
        if(vps){
            var profile=new Properties();
            try(var input=new ClassPathResource("application-vps.properties").getInputStream()){profile.load(input);}
            sources.addFirst(new PropertiesPropertySource("vps",profile));
        }
        if(withSecret)sources.addFirst(new MapPropertySource("private-test",Map.of("JWT_SECRET","local-diagnostic-secret-with-at-least-32-bytes")));
        ctx.registerBean(PropertySourcesPlaceholderConfigurer.class);
        ctx.registerBean(ObjectMapper.class, () -> tools.jackson.databind.json.JsonMapper.builder().build());
        ctx.register(JwtService.class);
        return ctx;
    }
    @Test void devCanResolveItsExistingLocalDefault() throws Exception {
        try(var ctx=context(false,false)){
            ctx.refresh();var jwt=ctx.getBean(JwtService.class);
            assertThat(jwt.validar(jwt.emitir(1L,"local",List.of("CONSULTA"),null))).isPresent();
        }
    }
    @Test void missingVpsSecretReproducesTheExactReportedFailure() throws Exception {
        try(var ctx=context(true,false)){
            assertThatThrownBy(ctx::refresh).hasStackTraceContaining("Could not resolve placeholder 'JWT_SECRET'");
        }
    }
    @Test void providingTheSecretCreatesJwtServiceAndValidatesSignedTokens() throws Exception {
        try(var ctx=context(true,true)){
            ctx.refresh();var jwt=ctx.getBean(JwtService.class);
            assertThat(jwt.validar(jwt.emitir(1L,"local",List.of("CONSULTA"),null))).isPresent();
        }
    }
}
