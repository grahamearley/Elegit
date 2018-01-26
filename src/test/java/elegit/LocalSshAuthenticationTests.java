package elegit;

import com.jcraft.jsch.JSch;
import elegit.exceptions.CancelledAuthorizationException;
import elegit.exceptions.MissingRepoException;
import elegit.models.AuthMethod;
import elegit.models.ClonedRepoHelper;
import elegit.models.ExistingRepoHelper;
import elegit.models.RepoHelper;
import elegit.sshauthentication.ElegitUserInfoTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.keyprovider.MappedKeyPairProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.git.pack.GitPackCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.KeySetPublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.TransportGitSsh;
import org.eclipse.jgit.transport.TransportProtocol;
import org.eclipse.jgit.transport.URIish;
import org.junit.*;
import sharedrules.TestUtilities;
import sharedrules.TestingLogPathRule;
import sharedrules.TestingRemoteAndLocalReposRule;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

public class LocalSshAuthenticationTests {

    @ClassRule
    public static final TestingLogPathRule testingLogPath = new TestingLogPathRule();

    @BeforeClass
    public static void setUpClass() {
        // Uncomment for debugging purposes
        //TestingRemoteAndLocalReposRule.doNotDeleteTempFiles();
    }

    @Rule
    public final TestingRemoteAndLocalReposRule testingRemoteAndLocalRepos =
            new TestingRemoteAndLocalReposRule(false);
    private Path directoryPath;

    private static final Logger console = LogManager.getLogger("briefconsolelogger");

    private static final String testPassword = "a_test_password";

    // Used for each test; declared up here so that it can be stopped if need be in
    private SshServer sshd;

    @Before
    public void setUp()  {
        console.info("Unit test started");
        directoryPath = testingRemoteAndLocalRepos.getDirectoryPath();
        sshd = null;
    }

    @After
    public void tearDown() throws Exception {
        if (sshd != null) {
            sshd.stop();
        }
    }

    // http://www.jcraft.com/jsch/examples/Logger.java.html
    public static class MyLogger implements com.jcraft.jsch.Logger {
        static java.util.Hashtable<Integer,String> name=new java.util.Hashtable<>();
        static{
            name.put(DEBUG, "DEBUG: ");
            name.put(INFO, "INFO: ");
            name.put(WARN, "WARN: ");
            name.put(ERROR, "ERROR: ");
            name.put(FATAL, "FATAL: ");
        }
        public boolean isEnabled(int level){
            return true;
        }
        public void log(int level, String message){
            System.err.print(name.get(level));
            System.err.println(message);
        }
    }


    @Test
    public void testSshPrivateKey() throws Exception {
        // Uncomment this to get detail SSH logging info, for debugging
        //JSch.setLogger(new MyLogger());

        // Set up test SSH server.
        sshd = SshServer.setUpDefaultServer();

        String remoteURL = TestUtilities.setUpTestSshServer(sshd,
                                                            directoryPath,
                                                            testingRemoteAndLocalRepos.getRemoteFull(),
                                                            testingRemoteAndLocalRepos.getRemoteBrief());

        console.info("Connecting to " + remoteURL);
        Path local = testingRemoteAndLocalRepos.getLocalFull();
        InputStream passwordFileStream = TestUtilities.class.getResourceAsStream("/rsa_key1_passphrase.txt");
        Scanner scanner = new Scanner(passwordFileStream);
        String passphrase = scanner.next();
        console.info("phrase is " + passphrase);
        String privateKeyFileLocation = "/rsa_key1";


        ClonedRepoHelper helper =
                new ClonedRepoHelper(local, "",
                                     new ElegitUserInfoTest(null, passphrase),
                                     getClass().getResource(privateKeyFileLocation).getFile(),
                                     directoryPath.resolve("testing_known_hosts").toString());
        helper.obtainRepository(remoteURL);

        // Verify that it is an SSH connection, then try a fetch
        assertEquals(helper.getCompatibleAuthentication(), AuthMethod.SSH);
        helper.fetch(false);

        // Create a new test file at the local repo
        Path fileLocation = local.resolve("README.md");
        FileWriter fw = new FileWriter(fileLocation.toString(), true);
        fw.write("start");
        fw.close();

        // Commit, and push to remote
        helper.addFilePathTest(fileLocation);
        helper.commit("Appended to file");
        PushCommand command = helper.prepareToPushAll();
        helper.pushAll(command);

        // Other methods to test if authentication is succeeding
        helper.getRefsFromRemote(false);
    }


    @Test
    public void testSshPassword() throws Exception {

        sshd = SshServer.setUpDefaultServer();
        String remoteURL = TestUtilities.setUpTestSshServer(sshd,
                                                            directoryPath,
                                                            testingRemoteAndLocalRepos.getRemoteFull(),
                                                            testingRemoteAndLocalRepos.getRemoteBrief());

        console.info("Connecting to " + remoteURL);
        Path local = testingRemoteAndLocalRepos.getLocalFull();
        ClonedRepoHelper helper =
                new ClonedRepoHelper(local, testPassword,
                                     new ElegitUserInfoTest(null, null),
                                     null, null);
        helper.obtainRepository(remoteURL);

        assertEquals(helper.getCompatibleAuthentication(), AuthMethod.SSH);
        helper.fetch(false);
        PushCommand command = helper.prepareToPushAll();
        helper.pushAll(command);

    }

    @Test
    public void testLsSshPassword() throws Exception {
        sshd = SshServer.setUpDefaultServer();
        String remoteURL = TestUtilities.setUpTestSshServer(sshd,
                                                            directoryPath,
                                                            testingRemoteAndLocalRepos.getRemoteFull(),
                                                            testingRemoteAndLocalRepos.getRemoteBrief());
        TransportCommand command = Git.lsRemoteRepository().setRemote(remoteURL);
        RepoHelper helper = new RepoHelper(null, new ElegitUserInfoTest(testPassword, null));
        helper.wrapAuthentication(command);
        command.call();
    }


    @Test
    public void testTransportProtocols() throws Exception {
        List<TransportProtocol> protocols = TransportGitSsh.getTransportProtocols();
        for (TransportProtocol protocol : protocols) {
            System.out.println(protocol + " " + protocol.getName());
            for (String scheme : protocol.getSchemes()) {
                System.out.println("\t" + scheme);
            }
        }
        for (TransportProtocol protocol : protocols) {
            if (protocol.canHandle(new URIish("https://anything.com/repo.git"))) {
                assertEquals(protocol.getName(), "HTTP");
                assertNotEquals(protocol.getName(), "SSH");
            }

            if (protocol.canHandle(new URIish("ssh://anything.com/repo.git"))) {
                assertEquals(protocol.getName(), "SSH");
                assertNotEquals(protocol.getName(), "HTTP");
            }
        }
    }

}
