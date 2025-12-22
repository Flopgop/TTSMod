package net.flamgop.ttsmod.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.stream.Stream;

public class NativeHelper {
    public static Path extractNative(String resourcePath) {
        Path outputPath = Path.of(resourcePath);

        if (Files.exists(outputPath)) {
            return outputPath;
        }

        try (InputStream in = TTSModClient.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) throw new IOException("Resource not found: " + resourcePath);

            File file = outputPath.toFile();
            file.mkdirs();

            Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputPath;
    }

    public static Path extractData(String resourcePath) {
        Path targetPath = Path.of(resourcePath);

        if (Files.exists(targetPath)) {
            return targetPath;
        }

        try {
            URL resourceUrl = NativeHelper.class.getResource("/" + resourcePath);
            if (resourceUrl == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            URI uri = resourceUrl.toURI();

            if (uri.getScheme().equals("jar")) {
                FileSystem fs;
                try {
                    fs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException e) {
                    fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                }

                copyFromPath(fs.getPath("/" + resourcePath), targetPath);
            } else {
                copyFromPath(Paths.get(uri), targetPath);
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Could not extract directory: " + resourcePath, e);
        }

        return targetPath;
    }

    private static void copyFromPath(Path source, Path targetPath) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(path -> {
                try {
                    Path relativePath = source.relativize(path);
                    Path destination = targetPath.resolve(relativePath.toString());

                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy: " + path, e);
                }
            });
        }
    }
}
