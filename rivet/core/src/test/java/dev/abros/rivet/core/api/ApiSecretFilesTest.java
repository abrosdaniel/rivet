package dev.abros.rivet.core.api;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ApiSecretFilesTest {
    @TempDir Path root;
    @Test void existingDeliveryIsNeverOverwrittenOrDeletedOnFailure()throws Exception{
        UUID id=UUID.randomUUID();Path file=ApiSecretFiles.write(root,id,"first");
        assertThrows(FileAlreadyExistsException.class,()->ApiSecretFiles.write(root,id,"second"));
        assertEquals("first\n",Files.readString(file));ApiSecretFiles.remove(root,id);assertFalse(Files.exists(file));
    }
    @Test void removalDoesNotFollowSymbolicLink()throws Exception{
        UUID id=UUID.randomUUID();Path file=ApiSecretFiles.write(root,id,"first");Files.delete(file);
        Path target=root.resolve("unrelated");Files.writeString(target,"keep");
        try{Files.createSymbolicLink(file,target);}catch(UnsupportedOperationException|java.io.IOException unsupported){org.junit.jupiter.api.Assumptions.abort("No symlink support");}
        ApiSecretFiles.remove(root,id);assertEquals("keep",Files.readString(target));assertFalse(Files.exists(file,LinkOption.NOFOLLOW_LINKS));
    }
}
