package org.me;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.common.util.threads.ThreadUtils;
import org.apache.sshd.server.ServerAuthenticationManager;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.hostbased.AcceptAllHostBasedAuthenticator;
import org.apache.sshd.server.auth.keyboard.DefaultKeyboardInteractiveAuthenticator;
import org.apache.sshd.server.command.AbstractCommandSupport;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.UnknownCommand;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.UploadPack;

public class SimpleGitServer {

    private final Repository repository;
    
    private final List<KeyPair> hostKeys;
    
    private final SshServer server;

    private final CloseableExecutorService executorService = ThreadUtils
            .newFixedThreadPool("SimpleGitServer", 4);

    public SimpleGitServer(Repository repository, KeyPair... hostKeys) {
        this.repository = repository;
        this.hostKeys = Arrays.asList(hostKeys);
        this.server = SshServer.setUpDefaultServer();

        this.server.setKeyPairProvider((session) -> this.hostKeys);
        
        configureAuthentication();
        
        List<NamedFactory<Command>> subsystems = configureSubsystems();
        
        if (!subsystems.isEmpty()) {
            this.server.setSubsystemFactories(subsystems);
        }

        disableShell();

        this.server.setCommandFactory(command -> {
            if (command.startsWith(RemoteConfig.DEFAULT_UPLOAD_PACK)) {
                return new GitUploadPackCommand(command, executorService);
            } else if (command.startsWith(RemoteConfig.DEFAULT_RECEIVE_PACK)) {
                return new GitReceivePackCommand(command, executorService);
            }
            return new UnknownCommand(command);
        });
        
        
    }
    
    private void disableShell() {
        server.setShellFactory(null);
    }
    
    private void configureAuthentication() {
        server.setUserAuthFactories(getAuthFactories());
        server.setPasswordAuthenticator((user, pwd, session) -> {
            return true;
        });
        server.setKeyboardInteractiveAuthenticator(new DefaultKeyboardInteractiveAuthenticator() {
            @Override
            public boolean authenticate(ServerSession session, String username, List<String> responses)
                    throws Exception {
                // TODO Auto-generated method stub
                return true;
            }
        });
        server.setHostBasedAuthenticator(AcceptAllHostBasedAuthenticator.INSTANCE);
       
        server.setPublickeyAuthenticator((userName, publicKey, session) -> {
            return true;
        });
    }
    
    private List<NamedFactory<UserAuth>> getAuthFactories() {
        List<NamedFactory<UserAuth>> authentications = new ArrayList<>();
       
        authentications.add(
                ServerAuthenticationManager.DEFAULT_USER_AUTH_PUBLIC_KEY_FACTORY);
        authentications.add(
                ServerAuthenticationManager.DEFAULT_USER_AUTH_KB_INTERACTIVE_FACTORY);
//        authentications.add(
//                ServerAuthenticationManager.DEFAULT_USER_AUTH_PASSWORD_FACTORY);
        return authentications;
    }
    
    private List<NamedFactory<Command>> configureSubsystems() {
        // SFTP.
        server.setFileSystemFactory(new VirtualFileSystemFactory() {

            @Override
            protected Path computeRootDir(Session session) throws IOException {
                return SimpleGitServer.this.repository.getDirectory()
                        .getParentFile().getAbsoluteFile().toPath();
            }
        });
        return Collections
                .singletonList((new SftpSubsystemFactory.Builder()).build());
    }
    
    public int start() throws IOException {
        server.start();
        return server.getPort();
    }

    public void stop() throws IOException {
        executorService.shutdownNow();
        server.stop(true);
    }
    
    private class GitUploadPackCommand extends AbstractCommandSupport {

        protected GitUploadPackCommand(String command,
                CloseableExecutorService executorService) {
            super(command, ThreadUtils.noClose(executorService));
        }

        @Override
        public void run() {
            UploadPack uploadPack = new UploadPack(repository);
            String gitProtocol = getEnvironment().getEnv().get("GIT_PROTOCOL");
            if (gitProtocol != null) {
                uploadPack
                        .setExtraParameters(Collections.singleton(gitProtocol));
            }
            try {
                uploadPack.upload(getInputStream(), getOutputStream(),
                        getErrorStream());
                onExit(0);
            } catch (IOException e) {
                log.warn(
                        MessageFormat.format("Could not run {0}", getCommand()),
                        e);
                onExit(-1, e.toString());
            }
        }

    }

    private class GitReceivePackCommand extends AbstractCommandSupport {

        protected GitReceivePackCommand(String command,
                CloseableExecutorService executorService) {
            super(command, ThreadUtils.noClose(executorService));
        }

        @Override
        public void run() {
            try {
                new ReceivePack(repository).receive(getInputStream(),
                        getOutputStream(), getErrorStream());
                onExit(0);
            } catch (IOException e) {
                log.warn(
                        MessageFormat.format("Could not run {0}", getCommand()),
                        e);
                onExit(-1, e.toString());
            }
        }

    }

    
}
