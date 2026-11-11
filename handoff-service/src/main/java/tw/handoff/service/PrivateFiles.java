package tw.handoff.service;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.IOException;
final class PrivateFiles {
 static void write(Path path,String value)throws IOException{
  Files.createDirectories(path.toAbsolutePath().getParent());
  if(Files.isSymbolicLink(path))throw new IOException("Symbolic credential file refused");
  try{Files.createFile(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));}catch(FileAlreadyExistsException exists){}catch(UnsupportedOperationException windows){if(!Files.exists(path))Files.createFile(path);}
  restrict(path,false);Files.writeString(path,value,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
 }
 static void restrict(Path path,boolean directory)throws IOException{
  if(Files.getFileStore(path).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));
 }
}
