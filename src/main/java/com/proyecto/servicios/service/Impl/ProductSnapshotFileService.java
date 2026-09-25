package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.model.product.ProductListResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
public class ProductSnapshotFileService {

    private static final Set<PosixFilePermission> OWNER_DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    private final Path baseDirectory;
    private final XmlMapper xmlMapper;

    @Autowired
    public ProductSnapshotFileService(
            @Value("${product.sync.temp-directory}") String baseDirectory
    ) {
        this.baseDirectory = Path.of(baseDirectory).toAbsolutePath().normalize();
        this.xmlMapper = new XmlMapper();
    }

    public Path writeTemporarySnapshot(ProductListResponse response) {
        Path runDirectory = null;
        try {
            Files.createDirectories(baseDirectory);
            applyPermissions(baseDirectory, OWNER_DIRECTORY_PERMISSIONS);

            runDirectory = Files.createTempDirectory(baseDirectory, "run-");
            applyPermissions(runDirectory, OWNER_DIRECTORY_PERMISSIONS);

            Path snapshot = runDirectory.resolve("products.xml");
            xmlMapper.writeValue(snapshot.toFile(), response);
            applyPermissions(snapshot, OWNER_FILE_PERMISSIONS);
            log.info("Snapshot temporal del catálogo creado en una carpeta de ejecución segura");
            return snapshot;
        } catch (Exception exception) {
            if (runDirectory != null) {
                deleteRunDirectory(runDirectory);
            }
            throw new ProductIntegrationException(
                    ProductIntegrationErrorType.DATABASE_ERROR,
                    "Gestopago respondió, pero el catálogo no pudo serializarse en el archivo temporal",
                    exception
            );
        }
    }

    public void deleteSnapshot(Path snapshot) {
        if (snapshot == null) {
            return;
        }
        Path runDirectory = snapshot.toAbsolutePath().normalize().getParent();
        if (runDirectory == null || !runDirectory.startsWith(baseDirectory)
                || runDirectory.equals(baseDirectory)) {
            log.error("Se rechazó la limpieza de una ruta fuera del directorio temporal configurado");
            return;
        }
        deleteRunDirectory(runDirectory);
    }

    private void deleteRunDirectory(Path runDirectory) {
        try (Stream<Path> paths = Files.walk(runDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deletePathSafely);
        } catch (IOException exception) {
            log.warn("No fue posible limpiar completamente la carpeta temporal del catálogo");
        }
    }

    private void deletePathSafely(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("No fue posible eliminar un archivo temporal del catálogo");
        }
    }

    private void applyPermissions(Path path, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows aplica los permisos ACL del usuario que creó el archivo.
        }
    }
}
