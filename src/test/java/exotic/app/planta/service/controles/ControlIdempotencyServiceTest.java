package exotic.app.planta.service.controles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import exotic.app.planta.model.controles.RegistroIdempotenciaControl;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.RegistroIdempotenciaControlRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ControlIdempotencyServiceTest {
    private RegistroIdempotenciaControlRepo repo;
    private ControlIdempotencyService service;
    private RegistroIdempotenciaControl registro;
    private User actor;

    @BeforeEach
    void setUp() {
        repo = mock(RegistroIdempotenciaControlRepo.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new ControlIdempotencyService(repo, mapper);
        registro = new RegistroIdempotenciaControl();
        actor = new User();
        actor.setId(17L);
        actor.setUsername("operador");

        AtomicBoolean insertado = new AtomicBoolean();
        when(repo.insertarSiAusente(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any())).thenAnswer(invocation -> {
            if (!insertado.compareAndSet(false, true)) return 0;
            registro.setActor(actor);
            registro.setAccion(invocation.getArgument(1));
            registro.setRecurso(invocation.getArgument(2));
            registro.setClave(invocation.getArgument(3));
            registro.setHuellaPayload(invocation.getArgument(4));
            registro.setCreadaEn(invocation.getArgument(5));
            return 1;
        });
        when(repo.buscarParaActualizar(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(registro));
        when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void mismaClaveYHuellaDevuelveRespuestaOriginalSinRepetirLaMutacion() {
        AtomicInteger invocaciones = new AtomicInteger();

        TestResponse primera = service.ejecutar(
                actor, "EJECUTAR", "control/31", "key-12345678",
                new TestRequest("valor"), TestResponse.class,
                () -> new TestResponse(91L, "resultado-" + invocaciones.incrementAndGet()));
        TestResponse repetida = service.ejecutar(
                actor, "EJECUTAR", "control/31", "key-12345678",
                new TestRequest("valor"), TestResponse.class,
                () -> new TestResponse(92L, "resultado-" + invocaciones.incrementAndGet()));

        assertEquals(primera, repetida);
        assertEquals(1, invocaciones.get());
        assertNotNull(registro.getCompletadaEn());
        assertNotNull(registro.getRespuestaJson());
    }

    @Test
    void mismaClaveConPayloadDiferenteProduceConflictoSinNuevaMutacion() {
        service.ejecutar(
                actor, "EJECUTAR", "control/31", "key-12345678",
                new TestRequest("primero"), TestResponse.class,
                () -> new TestResponse(91L, "original"));
        AtomicInteger invocaciones = new AtomicInteger();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.ejecutar(
                        actor, "EJECUTAR", "control/31", "key-12345678",
                        new TestRequest("diferente"), TestResponse.class,
                        () -> new TestResponse(92L, "nuevo-" + invocaciones.incrementAndGet())));

        assertTrue(error.getMessage().contains("payload diferente"));
        assertEquals(0, invocaciones.get());
    }

    @Test
    void exigeClaveExplicitaValidaAntesDeConsultarPersistencia() {
        assertThrows(IllegalArgumentException.class, () -> service.ejecutar(
                actor, "EJECUTAR", "control/31", "  ",
                new TestRequest("valor"), TestResponse.class,
                () -> new TestResponse(91L, "resultado")));

        verifyNoInteractions(repo);
    }

    private record TestRequest(String valor) {}
    private record TestResponse(Long id, String valor) {}
}
