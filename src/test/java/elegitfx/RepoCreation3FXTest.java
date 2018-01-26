package elegitfx;

import elegit.Main;
import elegit.controllers.BusyWindow;
import elegit.controllers.SessionController;
import elegit.exceptions.CancelledAuthorizationException;
import elegit.exceptions.MissingRepoException;
import elegit.exceptions.NoCommitsToPushException;
import elegit.exceptions.PushToAheadRemoteError;
import elegit.models.*;
import elegit.monitors.RepositoryMonitor;
import elegit.sshauthentication.ElegitUserInfoTest;
import elegit.treefx.Cell;
import elegit.treefx.CellState;
import elegit.treefx.TreeLayout;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.loadui.testfx.GuiTest;
import org.testfx.api.FxAssert;
import org.testfx.framework.junit.ApplicationTest;
import sharedrules.TestUtilities;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.prefs.Preferences;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class RepoCreation3FXTest extends ApplicationTest {

    static {
        // -----------------------Logging Initialization Start---------------------------
        Path logPath = Paths.get("logs");
        String s = logPath.toAbsolutePath().toString();
        System.setProperty("logFolder", s);
    }

    private static final Logger logger = LogManager.getLogger("consolelogger");

    private static final Random random = new Random(90125);

    private SessionController sessionController;

    private Path directoryPath;

    private Stage stage;

    @Rule
    public TestName testName = new TestName();

    @Before
    public void setup() throws Exception {
        logger.info("Unit test started");
        directoryPath = Files.createTempDirectory("unitTestRepos");
        directoryPath.toFile().deleteOnExit();
        initializeLogger();
        logger.info("Test name: " + testName.getMethodName());
    }


    // Helper method to avoid annoying traces from logger
    void initializeLogger() throws IOException {
        // Create a temp directory for the files to be placed in
        Path logPath = Files.createTempDirectory("elegitLogs");
        logPath.toFile().deleteOnExit();
        System.setProperty("logFolder", logPath.toString());
    }

    @After
    public void tearDown() {
        logger.info("Tearing down");
        assertEquals(0, Main.getAssertionCount());
    }


    @Override
    public void start(Stage stage) throws Exception {
        sessionController = TestUtilities.commonTestFXstart(stage);
    }


    @Test
    public void countOfCommitsInTreeTest() throws Exception {

        // Make two repos; swap between them, make sure number of commits is correct in tree
        logger.info("Temp directory: " + directoryPath);

        Path remote1 = directoryPath.resolve("remote1");
        Path local1 = directoryPath.resolve("local1");
        RevCommit firstCommit1 = makeTestRepo(remote1, local1, 5);
        logger.info(remote1);
        logger.info(local1);

        Path remote2 = directoryPath.resolve("remote2");
        Path local2 = directoryPath.resolve("local2");
        RevCommit firstCommit2 = makeTestRepo(remote2, local2, 5);
        logger.info(remote2);
        logger.info(local2);

        SessionController.gitStatusCompletedOnce = new CountDownLatch(1);

        clickOn("#loadNewRepoButton")
                .clickOn("#loadExistingRepoOption")
                .clickOn("#repoInputDialog")
                .write(local1.toString())
                .clickOn("#repoInputDialogOK");

        SessionController.gitStatusCompletedOnce.await();

        Cell firstCell1 = lookup(Matchers.hasToString(firstCommit1.getName())).query();
        assertNotEquals(null, firstCell1);

        Set<Cell> cells1 = lookup(Matchers.instanceOf(Cell.class)).queryAll();
        logger.info("Commits added 1");
        cells1.stream().forEach(logger::info);
        assertEquals(6,cells1.size());

        clickOn("#loadNewRepoButton")
                .clickOn("#loadExistingRepoOption")
                .clickOn("#repoInputDialog")
                .write(local2.toString())
                .clickOn("#repoInputDialogOK");


        Cell firstCell2 = lookup(Matchers.hasToString(firstCommit2.getName())).query();
        assertNotEquals(null, firstCell2);

        sleep(3000);

        Set<Cell> cells2 = lookup(Matchers.instanceOf(Cell.class)).queryAll();
        logger.info("Commits added 2");
        cells2.stream().forEach(logger::info);
        assertEquals(6,cells2.size());
    }

    private RevCommit makeTestRepo(Path remote, Path local, int numCommits) throws GitAPIException, IOException, CancelledAuthorizationException, MissingRepoException, PushToAheadRemoteError, NoCommitsToPushException {
        Git.init().setDirectory(remote.toFile()).setBare(true).call();
        Git.cloneRepository().setDirectory(local.toFile()).setURI("file://" + remote).call();

        ExistingRepoHelper helper = new ExistingRepoHelper(local, new ElegitUserInfoTest());

        Path fileLocation = local.resolve("README.md");

        FileWriter fw = new FileWriter(fileLocation.toString(), true);
        fw.write("start"+random.nextInt()); // need this to make sure each repo comes out with different hashes
        fw.close();
        helper.addFilePathTest(fileLocation);
        RevCommit firstCommit = helper.commit("Appended to file");
        Cell firstCellAttempt = lookup(firstCommit.getName()).query();
        logger.info("firstCell = " + firstCellAttempt);

        for (int i = 0; i < numCommits; i++) {
            fw = new FileWriter(fileLocation.toString(), true);
            fw.write("" + i);
            fw.close();
            helper.addFilePathTest(fileLocation);
            helper.commit("Appended to file");
        }

        // Just push all untracked local branches
        PushCommand command = helper.prepareToPushAll(untrackedLocalBranches -> untrackedLocalBranches);
        helper.pushAll(command);

        return firstCommit;
    }

}