package tw.handoff.service;
import java.io.IOException;
import java.nio.file.*;
final class PairingFile {
 /** Writes only when the content differs, so an unchanged pairing keeps its modification time. Returns whether the file was written. */
 static boolean writeIfChanged(Path file,String content)throws IOException{
  if(Files.isSymbolicLink(file))throw new IOException("Symbolic credential file refused");
  if(Files.isRegularFile(file)){try{if(Files.readString(file).equals(content)){PrivateFiles.restrict(file,false);return false;}}catch(java.nio.charset.CharacterCodingException unreadable){}}
  PrivateFiles.write(file,content);return true;
 }
}
