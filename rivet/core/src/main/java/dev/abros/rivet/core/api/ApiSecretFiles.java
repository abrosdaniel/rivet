package dev.abros.rivet.core.api;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Local delivery only. Never silently falls back to a file readable by other OS users. */
public final class ApiSecretFiles {
    private ApiSecretFiles() {}
    public static Path write(Path gameRoot, UUID id, String secret) throws IOException {
        Path root = gameRoot.toRealPath();
        Path rivet = root.resolve("rivet");
        if (!Files.exists(rivet, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(rivet);
        directory(rivet);
        Path dir = rivet.resolve("api-keys");
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(dir, protection(root, true));
        directory(dir);
        if (!Files.getOwner(dir).equals(Files.getOwner(root))) throw new IOException("API key directory owner mismatch");
        protect(dir, root, true);
        Path file = dir.resolve(id + ".secret");
        boolean created = false;
        try (var channel = FileChannel.open(file, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), protection(root, false))) {
            created = true;
            byte[] bytes = (secret + "\n").getBytes(StandardCharsets.US_ASCII);
            try { var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true); }
            finally { Arrays.fill(bytes, (byte) 0); }
        } catch (IOException | RuntimeException failure) {
            if (created) try { Files.deleteIfExists(file); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        return file;
    }
    public static void remove(Path gameRoot, UUID id) throws IOException {
        Path rivet = gameRoot.toRealPath().resolve("rivet");
        if (!Files.exists(rivet, LinkOption.NOFOLLOW_LINKS)) return;
        directory(rivet);
        Path dir = rivet.resolve("api-keys");
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) return;
        directory(dir);
        Files.deleteIfExists(dir.resolve(id + ".secret"));
    }
    private static void directory(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Unsafe API key directory");
    }
    private static FileAttribute<?> protection(Path root, boolean directory) throws IOException {
        if (Files.getFileStore(root).supportsFileAttributeView(PosixFileAttributeView.class))
            return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        if (Files.getFileStore(root).supportsFileAttributeView(AclFileAttributeView.class)) {
            var permissions = EnumSet.allOf(AclEntryPermission.class);
            var acl = List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(root)).setPermissions(permissions).build());
            return new FileAttribute<List<AclEntry>>() {
                public String name() { return "acl:acl"; }
                public List<AclEntry> value() { return acl; }
            };
        }
        throw new IOException("Filesystem cannot protect API key delivery");
    }
    @SuppressWarnings("unchecked") private static void protect(Path path, Path root, boolean directory) throws IOException {
        var attribute = protection(root, directory);
        Files.setAttribute(path, attribute.name(), attribute.value(), LinkOption.NOFOLLOW_LINKS);
    }
}
