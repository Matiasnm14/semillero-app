package edu.upb.semillero.service.video;

import java.nio.file.Path;

public interface VideoInspector {
    record Metadata(String format, Double durationSeconds) {}
    Metadata inspect(Path file);
}
