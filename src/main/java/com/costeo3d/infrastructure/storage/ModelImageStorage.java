package com.costeo3d.infrastructure.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Copias locales de imágenes de modelos 3D.
 *
 * En MySQL se guarda solamente «models/UUID.png» (o .jpg/.jpeg).
 * El directorio de imágenes no forma parte del repositorio ni de resources.
 * No contiene operaciones de base de datos: el formulario coordina copia y alta.
 */
public final class ModelImageStorage {
    private static final long MAX_BYTES = 12L * 1024L * 1024L;
    private static final long MAX_PIXELS = 36_000_000L;
    private static final Pattern MANAGED_REFERENCE = Pattern.compile(
            "models/[a-f0-9-]{36}\\.(?:png|jpg|jpeg)");

    private ModelImageStorage() { }

    /** Ubicación persistente: LOCALAPPDATA en Windows o ~/.costeo3d/images en otros SO. */
    public static Path imageRoot() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                && localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "Costeo3D", "images").toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".costeo3d", "images")
                .toAbsolutePath().normalize();
    }

    /** Revisa que el archivo sea realmente PNG o JPEG y tenga un tamaño razonable. */
    public static void validateSelectedImage(Path source) throws IOException {
        Objects.requireNonNull(source, "El archivo es obligatorio.");
        if (!Files.isRegularFile(source) || !Files.isReadable(source)) {
            throw new IOException("No se puede leer el archivo seleccionado.");
        }
        long size = Files.size(source);
        if (size <= 0 || size > MAX_BYTES) {
            throw new IOException("La imagen debe ocupar como máximo 12 MB.");
        }
        String extension = extensionOf(source);
        if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg")) {
            throw new IOException("Seleccioná una imagen PNG o JPG/JPEG.");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) throw new IOException("No se pudo leer el formato de imagen.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("El archivo no contiene una imagen válida.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean png = format.equals("png") && extension.equals("png");
                boolean jpeg = (format.equals("jpeg") || format.equals("jpg"))
                        && (extension.equals("jpg") || extension.equals("jpeg"));
                if (!png && !jpeg) {
                    throw new IOException("El contenido no coincide con la extensión de la imagen.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("La imagen supera el máximo de 36 megapíxeles.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IndexOutOfBoundsException ex) {
            throw new IOException("El archivo no contiene una imagen válida.", ex);
        }
    }

    /**
     * Copia la imagen a la carpeta propia y devuelve la referencia relativa para MySQL.
     * Si el alta posterior en MySQL falla, llamar deleteManagedCopy(reference).
     */
    public static String copySelectedImage(Path source) throws IOException {
        validateSelectedImage(source);
        String extension = extensionOf(source);
        String filename = UUID.randomUUID() + "." + extension;
        Path folder = imageRoot().resolve("models");
        Files.createDirectories(folder);
        Path target = folder.resolve(filename);
        Path temporary = Files.createTempFile(folder, ".import-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            // Verificamos la copia antes de publicarla.
            validateSelectedImageWithKnownExtension(temporary, extension);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target);
            }
            return "models/" + filename;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    // El temporal no conserva su extensión; se verifica el contenido y tamaño nuevamente.
    private static void validateSelectedImageWithKnownExtension(Path temporary, String extension)
            throws IOException {
        long size = Files.size(temporary);
        if (size <= 0 || size > MAX_BYTES) {
            throw new IOException("La imagen excede el tamaño permitido.");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(temporary.toFile())) {
            if (input == null) throw new IOException("La copia de imagen no es válida.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("La copia de imagen no es válida.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(extension.equals("png") && format.equals("png"))
                        && !((extension.equals("jpg") || extension.equals("jpeg"))
                            && (format.equals("jpeg") || format.equals("jpg")))) {
                    throw new IOException("Formato incorrecto de la copia de imagen.");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    /** Solo elimina copias propias con referencia UUID; jamás el archivo original del usuario. */
    public static void deleteManagedCopy(String reference) throws IOException {
        if (!isManagedReference(reference)) return;
        Path root = imageRoot();
        Path path = root.resolve(reference).normalize();
        if (path.startsWith(root.resolve("models"))) {
            Files.deleteIfExists(path);
        }
    }

    /**
     * Resuelve las referencias actuales y también las rutas locales antiguas.
     * Las URL externas se ignoran. Nunca se accede a Internet.
     */
    public static Path resolveExistingImage(String reference) {
        if (reference == null || reference.isBlank() || reference.contains("://")) return null;
        try {
            Path path;
            if (isManagedReference(reference)) {
                path = imageRoot().resolve(reference).normalize();
            } else {
                // Compatibilidad con referencias absolutas o relativas registradas antes de E10.4.3.
                path = Path.of(reference).toAbsolutePath().normalize();
            }
            String ext = extensionOf(path);
            if (!(ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg"))) return null;
            return Files.isRegularFile(path) && Files.isReadable(path) ? path : null;
        } catch (SecurityException | InvalidPathException ex) {
            return null;
        }
    }

    private static boolean isManagedReference(String reference) {
        return reference != null && MANAGED_REFERENCE.matcher(reference).matches();
    }

    private static String extensionOf(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }
}
