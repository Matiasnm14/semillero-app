package edu.upb.semillero.service.video;

import edu.upb.semillero.service.exception.StorageUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Executes argument lists without a shell. Process output never goes to application logs. */
@Component
public class VideoProcessRunner {
    public record Result(int exitCode, String output) {}

    public Result run(List<String> command, Duration timeout) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new StorageUnavailableException("No se pudo iniciar la herramienta de video; revisa la configuración del servidor");
        }
        CompletableFuture<String> output = new CompletableFuture<>();
        Thread reader = Thread.startVirtualThread(() -> {
            try (var input = process.getInputStream(); var bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                boolean exceeded = false;
                while ((n = input.read(buffer)) != -1) {
                    if (bytes.size() + n <= 1024 * 1024) bytes.write(buffer, 0, n);
                    else exceeded = true;
                }
                if (exceeded) output.completeExceptionally(new IOException("Output limit"));
                else output.complete(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException e) {
                output.completeExceptionally(e);
            }
        });
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new StorageUnavailableException("La operación de video excedió el tiempo permitido; vuelve a intentarlo");
            }
            return new Result(process.exitValue(), output.get(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageUnavailableException("La operación de video fue interrumpida");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new StorageUnavailableException("No se pudo obtener la respuesta de la herramienta de video");
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            reader.interrupt();
        }
    }
}
