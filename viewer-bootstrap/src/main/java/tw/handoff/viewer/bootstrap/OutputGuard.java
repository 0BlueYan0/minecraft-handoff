package tw.handoff.viewer.bootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Same refusal rules as {@link ViewerSandbox#output}, but the real path of the root is resolved once and
 * folders that were already verified are not inspected again. That keeps tens of thousands of checks
 * to about one file-system call each.
 *
 * Guarantee: {@link #check} trusts folders verified earlier in this object's life, so it is for deciding
 * what to do. {@link #forWrite} resolves the direct parent folder to its real path before the first write
 * into that folder, so a folder replaced by a link after planning cached it is still refused. Later writes
 * into the same folder reuse that answer: a folder replaced by a link after a file was already written
 * into it during the same prepare is not re-resolved. One guard lives for one prepare.
 *
 * Copies run on several threads, so both caches are concurrent sets. Adding the same folder twice is harmless.
 */
final class OutputGuard {
    private final Path base;
    private final Set<Path> verified=ConcurrentHashMap.newKeySet();
    /** Parent folders that passed the real-path check of {@link #forWrite}. Planning never adds to this set. */
    private final Set<Path> writable=ConcurrentHashMap.newKeySet();
    OutputGuard(Path root)throws IOException{base=root.toRealPath();}

    /** Returns the attributes of the target without following links, or null when it does not exist. */
    BasicFileAttributes check(Path target)throws IOException{
        Path file=target.toAbsolutePath().normalize();
        if(!file.startsWith(base))throw new IOException("Escaping viewer output refused");
        verifyFolder(file.getParent());
        return attributes(file);
    }
    /**
     * Creates the parent folders of a target. Creating a folder is itself a write, so the nearest existing
     * ancestor is resolved to its real path first, which also resolves every folder above it.
     */
    void prepareParent(Path target)throws IOException{
        Path file=target.toAbsolutePath().normalize();
        if(!file.startsWith(base))throw new IOException("Escaping viewer output refused");
        Path parent=file.getParent(),existing=parent;
        if(writable.contains(parent))return;
        while(existing!=null&&!Files.exists(existing,LinkOption.NOFOLLOW_LINKS))existing=existing.getParent();
        if(existing==null||Files.isSymbolicLink(existing)||!existing.toRealPath().startsWith(base))throw new IOException("Linked viewer output refused");
        Files.createDirectories(parent);
    }
    /** Full check for a path that is about to be written. Call it after the parent folder exists. */
    Path forWrite(Path target)throws IOException{
        Path file=target.toAbsolutePath().normalize();
        BasicFileAttributes found=check(file);
        Path parent=file.getParent();
        if(!writable.contains(parent)){
            if(Files.isSymbolicLink(parent)||!parent.toRealPath().startsWith(base))throw new IOException("Linked viewer output refused");
            writable.add(parent);
        }
        if(found!=null&&(found.isSymbolicLink()||found.isOther()))throw new IOException("Linked viewer output refused");
        return file;
    }
    private BasicFileAttributes attributes(Path file)throws IOException{
        try{
            BasicFileAttributes found=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(found.isSymbolicLink()||found.isOther())throw new IOException("Linked viewer output refused");
            return found;
        }catch(NoSuchFileException missing){return null;}
    }
    private void verifyFolder(Path folder)throws IOException{
        if(folder==null||folder.equals(base)||verified.contains(folder))return;
        verifyFolder(folder.getParent());
        if(!Files.exists(folder,LinkOption.NOFOLLOW_LINKS))return;
        if(Files.isSymbolicLink(folder)||!folder.toRealPath().startsWith(base))throw new IOException("Linked viewer output refused");
        verified.add(folder);
    }
}
