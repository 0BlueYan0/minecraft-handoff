package tw.handoff.service;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.net.ssl.*;
import java.io.IOException;
final class TlsIdentity {
 private final SSLContext context;private final String fingerprint;
 SSLContext context(){return context;}String fingerprint(){return fingerprint;}
 private TlsIdentity(SSLContext context,String fingerprint){this.context=context;this.fingerprint=fingerprint;}
 static String randomToken(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 static TlsIdentity open(Path state)throws Exception{
  Files.createDirectories(state);PrivateFiles.restrict(state,true);
  Path passwordFile=state.resolve("keystore.password"),store=state.resolve("host.p12");
  if(Files.isSymbolicLink(passwordFile)||Files.isSymbolicLink(store))throw new IOException("Symbolic credential file refused");
  if(!Files.exists(passwordFile))PrivateFiles.write(passwordFile,randomToken());
  String password=Files.readString(passwordFile).trim();if(!password.matches("[A-Za-z0-9_-]{43}"))throw new IOException("Invalid keystore state");
  if(!Files.exists(store)){
   String suffix=System.getProperty("os.name").startsWith("Windows")?".exe":"";
   Path keytool=Path.of(System.getProperty("java.home"),"bin","keytool"+suffix);
   ProcessBuilder builder=new ProcessBuilder(keytool.toString(),"-genkeypair","-alias","handoff","-keyalg","EC","-groupname","secp256r1","-sigalg","SHA256withECDSA","-validity","730","-dname","CN=Minecraft Handoff private host","-storetype","PKCS12","-keystore",store.toAbsolutePath().toString(),"-storepass:env","HANDOFF_KEYSTORE_PASSWORD","-noprompt");
   builder.environment().put("HANDOFF_KEYSTORE_PASSWORD",password);builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
   Process process=builder.start();if(!process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Certificate creation timed out");}if(process.exitValue()!=0)throw new IOException("JDK keytool could not create the host identity");
   PrivateFiles.restrict(store,false);
  }
  KeyStore keys=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(store)){keys.load(in,password.toCharArray());}
  KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,password.toCharArray());
  SSLContext ctx=SSLContext.getInstance("TLS");ctx.init(km.getKeyManagers(),null,new SecureRandom());
  String pin=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(keys.getCertificate("handoff").getEncoded()));
  return new TlsIdentity(ctx,pin);
 }
}
