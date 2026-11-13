package tw.handoff.service;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PairingTokenTest {
    private static final String FORMAT="[A-Za-z0-9_-]{43}";
    @TempDir Path state;

    @Test void sameStateGivesSameToken()throws Exception{
        String first=TlsIdentity.pairingToken(state,false),second=TlsIdentity.pairingToken(state,false);
        assertTrue(first.matches(FORMAT));assertTrue(first.equals(second),"token changed between runs");
    }
    @Test void renewReplacesTheToken()throws Exception{
        String old=TlsIdentity.pairingToken(state,false),renewed=TlsIdentity.pairingToken(state,true);
        assertTrue(renewed.matches(FORMAT));assertFalse(old.equals(renewed),"renew kept the old token");
        assertTrue(renewed.equals(TlsIdentity.pairingToken(state,false)),"renewed token was not saved");
    }
    @Test void malformedTokenFileIsRegenerated()throws Exception{
        Files.writeString(state.resolve("pairing.token"),"short");
        String token=TlsIdentity.pairingToken(state,false);
        assertTrue(token.matches(FORMAT));assertTrue(token.equals(Files.readString(state.resolve("pairing.token")).trim()));
        Files.write(state.resolve("pairing.token"),new byte[]{(byte)0xff,(byte)0xfe,(byte)0xfd});
        assertTrue(TlsIdentity.pairingToken(state,false).matches(FORMAT));
    }
    @Test void symlinkTokenFileIsRefused()throws Exception{
        Path target=Files.writeString(state.resolve("elsewhere"),"a".repeat(43));
        try{Files.createSymbolicLink(state.resolve("pairing.token"),target);}
        catch(UnsupportedOperationException|IOException|SecurityException noSymlinks){Assumptions.abort("symlinks cannot be created here");}
        assertThrows(IOException.class,()->TlsIdentity.pairingToken(state,false));
        assertThrows(IOException.class,()->TlsIdentity.pairingToken(state,true));
    }
    @Test void tokenAndPairingFilesAreOwnerOnlyOnPosix()throws Exception{
        Assumptions.assumeTrue(Files.getFileStore(state).supportsFileAttributeView("posix"),"not a POSIX file system");
        var ownerOnly=java.nio.file.attribute.PosixFilePermissions.fromString("rw-------");
        Path token=state.resolve("pairing.token"),pairing=state.resolve("pairing.json");
        TlsIdentity.pairingToken(state,false);assertEquals(ownerOnly,Files.getPosixFilePermissions(token));
        assertTrue(PairingFile.writeIfChanged(pairing,"{\"a\":1}"));assertEquals(ownerOnly,Files.getPosixFilePermissions(pairing));
        Files.setPosixFilePermissions(pairing,java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        assertFalse(PairingFile.writeIfChanged(pairing,"{\"a\":1}"));
        assertEquals(ownerOnly,Files.getPosixFilePermissions(pairing));
    }
    @Test void pairingFileIsOnlyRewrittenWhenContentChanges()throws Exception{
        Path file=state.resolve("pairing.json");
        assertTrue(PairingFile.writeIfChanged(file,"{\"a\":1}"));
        FileTime old=FileTime.fromMillis(1_000_000_000_000L);Files.setLastModifiedTime(file,old);
        assertFalse(PairingFile.writeIfChanged(file,"{\"a\":1}"));
        assertEquals(old,Files.getLastModifiedTime(file));
        assertTrue(PairingFile.writeIfChanged(file,"{\"a\":2}"));
        assertEquals("{\"a\":2}",Files.readString(file));assertNotEquals(old,Files.getLastModifiedTime(file));
    }
}
