package com.filemover.infrastructure.storage.adapter;

import com.filemover.application.port.out.StorageGateway;
import com.filemover.infrastructure.config.FileMoverProperties.StorageConfig.SftpConfig;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class SftpStorageAdapter implements StorageGateway {

    private final SftpConfig config;

    public SftpStorageAdapter(SftpConfig config) {
        this.config = config;
    }

    @Override
    public void write(String path, InputStream content, long contentLength, String fileName) {
        String remotePath = config.getRemoteDir() + "/" + fileName;
        log.info("[SFTP] upload | host={} path={}", config.getHost(), remotePath);

        withChannel(channel -> {
            // Garante que o diretório remoto existe antes de gravar.
            // channel.stat() lança SftpException se o path não existir.
            try {
                channel.stat(config.getRemoteDir());
            } catch (SftpException e) {
                log.warn("[SFTP] Diretório remoto não encontrado, criando | dir={}", config.getRemoteDir());
                channel.mkdir(config.getRemoteDir());
            }
            channel.put(content, remotePath);
        });
    }

    @Override
    public InputStream read(String path) {
        // Abre sessão e canal, mas não os fecha aqui — serão fechados quando
        // o InputStream retornado for fechado pelo chamador (via FilterInputStream).
        try {
            Session session = buildSession();
            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();

            InputStream raw = channel.get(path);

            return new FilterInputStream(raw) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        channel.disconnect();
                        session.disconnect();
                    }
                }
            };
        } catch (JSchException | SftpException e) {
            throw new RuntimeException("Falha ao ler via SFTP: " + path, e);
        }
    }

    @Override
    public void delete(String path) {
        withChannel(channel -> channel.rm(path));
    }

    @Override
    public String providerName() {
        return "SFTP";
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void withChannel(SftpOperation op) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = buildSession();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            op.execute(channel);
        } catch (JSchException | SftpException e) {
            throw new RuntimeException("Falha na operação SFTP", e);
        } finally {
            // Garante desconexão mesmo em caso de exceção
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    private Session buildSession() throws JSchException {
        JSch jsch = new JSch();
        if (config.getPrivateKeyPath() != null && !config.getPrivateKeyPath().isBlank()) {
            jsch.addIdentity(config.getPrivateKeyPath());
        }
        Session session = jsch.getSession(config.getUsername(),
                config.getHost(),
                config.getPort());
        if (config.getPassword() != null) {
            session.setPassword(config.getPassword());
        }
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10_000);
        return session;
    }

    @FunctionalInterface
    interface SftpOperation {
        void execute(ChannelSftp channel) throws SftpException;
    }
}