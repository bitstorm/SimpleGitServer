package org.me;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;

public class ServerStarter {

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyFactory kf = KeyFactory.getInstance("RSA");
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(loadPEM("/test_private.pem")));
        PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(loadPEM("/test_public.pem")));
        
        KeyPair keyPair = new KeyPair(publicKey, privateKey);
        
        Repository repository = new FileRepository("<path to repo>.git");
        SimpleGitServer gitServer = new SimpleGitServer(repository, keyPair);
        
        gitServer.start();
    }
    
    private static byte[] loadPEM(String resource) throws IOException {
        URL url = ServerStarter.class.getResource(resource);
        InputStream in = url.openStream();
        String pem = new String(readAllBytes(in), StandardCharsets.ISO_8859_1);
        Pattern parse = Pattern.compile("(?m)(?s)^---*BEGIN.*---*$(.*)^---*END.*---*$.*");
        String encoded = parse.matcher(pem).replaceFirst("$1");
        return Base64.getDecoder().decode(encoded.replaceAll("\n", ""));
    }
    
    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos= new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        for (int read=0; read != -1; read = in.read(buf)) { baos.write(buf, 0, read); }
        return baos.toByteArray();
    }


}
