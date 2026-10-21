package edu.upb.semillero.service.video;

import edu.upb.semillero.config.VideoProperties;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.StorageUnavailableException;
import edu.upb.semillero.service.exception.VideoTooLargeException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LocalVideoStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalVideoStorage.class);
    private final VideoProperties properties;

    public record Staged(String storageId, Path path, long size, String sha256) {}

    public LocalVideoStorage(VideoProperties properties) {
        this.properties = properties;
    }

    public Staged stage(InputStream input) {
        String id = UUID.randomUUID().toString();
        Path directory = directory(id);
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve("upload.part");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (var output = Files.newOutputStream(target)) {
                byte[] bytes = new byte[1024 * 1024];
                int count;
                while ((count = input.read(bytes)) != -1) {
                    size += count;
                    if (size > properties.getMaxBytes()) throw new VideoTooLargeException();
                    output.write(bytes, 0, count);
                    digest.update(bytes, 0, count);
                }
            }
            if (size == 0) throw new BusinessException("El archivo de video está vacío");
            return new Staged(id, target, size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            discard(id);
            throw new StorageUnavailableException("No se pudo guardar el archivo original");
        } catch (RuntimeException e) {
            discard(id);
            throw e;
        }
    }

    public Path commit(Staged staged) {
        try {
            return Files.move(staged.path(), original(staged.storageId()), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new StorageUnavailableException("No se pudo confirmar el almacenamiento del original");
        }
    }

    public Path original(String id) {
        return directory(id).resolve("original.mp4");
    }

    public Path receipt(String id) {
        return directory(id).resolve("youtube-upload.json");
    }

    public boolean exists(String id) {
        return Files.isRegularFile(original(id));
    }

    public void discard(String id) {
        // Only called for uploads which never acquired a persistent record.
        Path directory = directory(id);
        try {
            Files.deleteIfExists(directory.resolve("upload.part"));
            Files.deleteIfExists(directory.resolve("original.mp4"));
            Files.deleteIfExists(directory);
        } catch (IOException e) {
            log.error("No se pudo limpiar una carga no confirmada: {}", id);
        }
    }

    private Path directory(String id) {
        UUID.fromString(id);
        return properties.getStorageDir().toAbsolutePath().normalize().resolve(id);
    }
}
