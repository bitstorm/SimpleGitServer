package org.me;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.common.util.threads.ThreadUtils;
import org.apache.sshd.server.SshServer;
import org.eclipse.jgit.lib.Repository;

public class SimpleGitServer {

    private final Repository repository;
    
    private final List<KeyPair> hostKeys = new ArrayList<>();
    
    private final SshServer server;
    
    private final PublicKey testKey;

    private final CloseableExecutorService executorService = ThreadUtils
            .newFixedThreadPool("SimpleGitServer", 4);

    public SimpleGitServer(Repository repository, SshServer server, PublicKey testKey) {
        this.repository = repository;
        this.server = server;
        this.testKey = testKey;
        
        
        
    }
    
    
}
