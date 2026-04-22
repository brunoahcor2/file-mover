package com.filemover.infrastructure.adapter.filesystem;

import com.filemover.application.port.out.FileStorageGateway;
import com.filemover.domain.exception.FileTransferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Adapter de sistema de arquivos: implementa FileStorageGateway
 * usando java.nio para operações atômicas e seguras.
 */
@Slf4j
@Component
public class LocalFileStorageAdapter implements FileStorageGateway {

    @Override
    public void moveFile(String sourcePath, String destinationPath) {
        Path source      = Paths.get(sourcePath);
        Path destination = Paths.get(destinationPath);

        log.debug("Moving file | source={} destination={}", source, destination);

        ensureParentDirectoryExists(destination);

        try {
            // Tenta move atômico primeiro (mesmo volume)
            Files.move(source, destination,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            log.info("File moved (atomic) | source={} destination={}", source, destination);

        } catch (IOException atomicEx) {
            log.debug("Atomic move failed, falling back to copy+delete | reason={}", atomicEx.getMessage());
            try {
                // Fallback: copy para destino, depois apaga origem
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(source);
                log.info("File moved (copy+delete) | source={} destination={}", source, destination);

            } catch (IOException fallbackEx) {
                log.error("Failed to move file | source={} destination={} error={}",
                        source, destination, fallbackEx.getMessage());
                throw new FileTransferException(source.getFileName().toString(),
                        "Could not move file to destination: " + fallbackEx.getMessage(), fallbackEx);
            }
        }
    }

    private void ensureParentDirectoryExists(Path destination) {
        Path parent = destination.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
                log.debug("Created destination directory | path={}", parent);
            } catch (IOException e) {
                throw new FileTransferException(destination.getFileName().toString(),
                        "Could not create destination directory: " + e.getMessage(), e);
            }
        }
    }
}
