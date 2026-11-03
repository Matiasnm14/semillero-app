package edu.upb.semillero.service.analysis;

import edu.upb.semillero.config.AnalysisProperties;
import edu.upb.semillero.service.exception.StorageUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Local durable copies of Spark heatmaps. Paths never come from the remote JSON. */
@Component
public class AnalysisArtifactStorage {
    private static final Logger log = LoggerFactory.getLogger(AnalysisArtifactStorage.class);
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private final AnalysisProperties properties;

    public AnalysisArtifactStorage(AnalysisProperties properties) { this.properties = properties; }

    public Path heatmap(String id) { return directory(id).resolve("heatmap.png"); }

    public boolean exists(String id) { return id != null && Files.isRegularFile(heatmap(id)); }

    /** Compensates a new upload whose database transaction could not commit. */
    public void discard(String id) {
        try {
            Files.deleteIfExists(heatmap(id));
            Files.deleteIfExists(directory(id));
        } catch (IOException e) { log.warn("No se pudo limpiar el mapa de una carga no confirmada: {}", id); }
    }

    public void store(String id, byte[] bytes) {
        if (bytes.length > properties.getMaxHeatmapBytes() || bytes.length < 24
                || !Arrays.equals(Arrays.copyOf(bytes, 8), PNG)) throw invalid();
        // Inspect dimensions before decoding: a tiny compressed image must not exhaust the heap.
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 24_000_000 || reader.read(0) == null) throw invalid();
            } finally { reader.dispose(); }
        } catch (IOException e) { throw invalid(); }
        Path temp = null;
        try {
            Files.createDirectories(directory(id));
            temp = Files.createTempFile(directory(id), "heatmap-", ".part");
            try (var output = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            Files.move(temp, heatmap(id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) {
                try (var directory = FileChannel.open(directory(id), StandardOpenOption.READ)) {
                    directory.force(true);
                }
            }
        } catch (IOException e) {
            throw new StorageUnavailableException("No se pudo guardar el mapa de calor del análisis");
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    private Path directory(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) throw invalid();
        return properties.getArtifactsDir().toAbsolutePath().normalize().resolve(id);
    }

    private AnalysisGatewayException invalid() {
        return new AnalysisGatewayException(AnalysisGatewayException.Reason.INVALID_RESPONSE);
    }
}
