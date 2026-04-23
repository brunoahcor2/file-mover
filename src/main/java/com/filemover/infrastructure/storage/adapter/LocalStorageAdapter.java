package com.filemover.infrastructure.storage.adapter;

import com.filemover.application.port.out.StorageGateway;
import com.filemover.infrastructure.config.FileMoverProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;

@Slf4j
@RequiredArgsConstructor
public class LocalStorageAdapter implements StorageGateway {

    private final FileMoverProperties properties;

    @Override
    public void write(String path, InputStream content, long contentLength, String fileName) {
        Path target = Path.of(properties.getDestinationDirectory(), fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("[LOCAL] arquivo gravado | path={}", target);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar arquivo local: " + target, e);
        }
    }

    @Override
    public InputStream read(String path) {
        try {
            return Files.newInputStream(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler arquivo local: " + path, e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao deletar arquivo local: " + path, e);
        }
    }

    @Override
    public String providerName() { return "LOCAL"; }
}