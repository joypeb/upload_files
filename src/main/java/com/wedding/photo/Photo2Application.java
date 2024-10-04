package com.wedding.photo;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


@SpringBootApplication
public class Photo2Application {
    private final Path UPLOAD_DIR = Paths.get("uploads");
    private static final long MAX_IMAGE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final long MAX_VIDEO_SIZE = 5L * 1024 * 1024 * 1024; // 5GB
    private static final int RANDOM_STRING_LENGTH = 10;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random RANDOM = new Random();

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp", "heic", "heif",
            "raw", "cr2", "nef", "arw", "dng", "raf", "rw2", "orf", "pef"
    ));

    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "avi", "mov", "wmv", "flv", "webm", "mkv", "m4v", "3gp", "mpeg", "mpg",
            "mxf", "r3d", "braw", "prores"
    ));

    public static void main(String[] args) {
        SpringApplication.run(Photo2Application.class, args);
    }

    @Bean
    public RouterFunction<ServerResponse> route() {
        return RouterFunctions.route()
                .GET("/", this::serveIndexHtml)
                .POST("/upload", this::handleFileUpload)
                .build();
    }

    private Mono<ServerResponse> serveIndexHtml(ServerRequest request) {
        Resource indexHtml = new ClassPathResource("static/index.html");
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(BodyInserters.fromResource(indexHtml));
    }

    private Mono<ServerResponse> handleFileUpload(ServerRequest request) {
        return request.multipartData()
                .flatMap(parts -> {
                    FilePart filePart = (FilePart) parts.getFirst("file");
                    FormFieldPart namePart = (FormFieldPart) parts.getFirst("name");
                    String name = (namePart != null) ? sanitizeName(namePart.value()) : "unknown";

                    String extension = getFileExtension(Objects.requireNonNull(filePart).filename());

                    return checkFileTypeAndSize(filePart)
                            .then(Mono.fromCallable(() -> generateUniqueFileName(name, extension)))
                            .flatMap(fileName -> {
                                Path filePath = UPLOAD_DIR.resolve(fileName);
                                return filePart.transferTo(filePath)
                                        .then(ServerResponse.ok().bodyValue(fileName));
                            })
                            .onErrorResume(e -> ServerResponse.badRequest().bodyValue(e.getMessage()));
                });
    }

    private String generateUniqueFileName(String baseName, String extension) {
        String fileName;
        do {
            String randomString = generateRandomString();
            fileName = baseName + "_" + randomString + "." + extension;
        } while (Files.exists(UPLOAD_DIR.resolve(fileName)));
        return fileName;
    }

    private String generateRandomString() {
        StringBuilder sb = new StringBuilder(Photo2Application.RANDOM_STRING_LENGTH);
        for (int i = 0; i < Photo2Application.RANDOM_STRING_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private Mono<Void> checkFileTypeAndSize(FilePart filePart) {
        String extension = getFileExtension(filePart.filename()).toLowerCase();
        if (!isValidFileType(extension)) {
            return Mono.error(new IllegalArgumentException("Invalid file type"));
        }

        return filePart.content()
                .reduce(0L, (acc, buffer) -> acc + buffer.readableByteCount())
                .flatMap(size -> {
                    long maxSize = isImage(extension) ? MAX_IMAGE_SIZE : MAX_VIDEO_SIZE;
                    if (size > maxSize) {
                        return Mono.error(new IllegalArgumentException("File size exceeds the limit"));
                    }
                    return Mono.empty();
                });
    }

    private boolean isValidFileType(String extension) {
        return IMAGE_EXTENSIONS.contains(extension) || VIDEO_EXTENSIONS.contains(extension);
    }

    private boolean isImage(String extension) {
        return IMAGE_EXTENSIONS.contains(extension);
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9가-힣._-]", "")
                   .replaceAll("\\s+", "_")
                   .toLowerCase();
    }

    private String getFileExtension(String fileName) {
        int lastIndexOf = fileName.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // 확장자가 없는 경우
        }
        return fileName.substring(lastIndexOf + 1).toLowerCase();
    }

    @PostConstruct
    private void createUploadDirectoryIfNotExists() {
        try {
            if (!Files.exists(UPLOAD_DIR)) {
                Files.createDirectories(UPLOAD_DIR);
                System.out.println("Upload directory created: " + UPLOAD_DIR);
            } else {
                System.out.println("Upload directory already exists: " + UPLOAD_DIR);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }
}