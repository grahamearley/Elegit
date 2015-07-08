package main.java.elegit;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import main.java.elegit.exceptions.*;
import org.controlsfx.control.NotificationPane;
import org.controlsfx.control.action.Action;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.NoMergeBaseException;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.prefs.BackingStoreException;

/**
 * The controller for the entire session.
 */
public class SessionController {

    public ComboBox<LocalBranchHelper> branchSelector;

    public Label currentRepoLabel;
    private static final double CURRENT_REPO_LABEL_MAX_WIDTH = 200;

    private SessionModel theModel;

    public NotificationPane notificationPane;
    public Button selectAllButton;
    public Button deselectAllButton;
    public Button switchUserButton;
    public Button clearRecentReposButton;

    public Button openRepoDirButton;
    public Button gitStatusButton;
    public Button commitButton;
    public Button mergeFromFetchButton;
    public Button pushButton;
    public Button fetchButton;
    public Button branchesButton;

    public ProgressIndicator fetchProgressIndicator;
    public ProgressIndicator pushProgressIndicator;

    public TextArea commitMessageField;
    public WorkingTreePanelView workingTreePanelView;
	public CommitTreePanelView localCommitTreePanelView;
    public CommitTreePanelView remoteCommitTreePanelView;

    public Circle remoteCircle;

    public TextField commitInfoNameText;
    public Label commitInfoAuthorText;
    public Label commitInfoDateText;
    public Button commitInfoNameCopyButton;
    public Button commitInfoGoToButton;
    public TextArea commitInfoMessageText;

    CommitTreeModel localCommitTreeModel;
    CommitTreeModel remoteCommitTreeModel;

    // The menu bar
    public MenuBar menuBar;
    private Menu newRepoMenu;
    private Menu openRecentRepoMenu;

    /**
     * Initializes the environment by obtaining the model
     * and putting the views on display.
     *
     * This method is automatically called by JavaFX.
     */
    public void initialize() {
        this.theModel = SessionModel.getSessionModel();

        this.initializeLayoutParameters();

        CommitTreeController.sessionController = this;

        this.workingTreePanelView.setSessionModel(this.theModel);
        this.localCommitTreeModel = new LocalCommitTreeModel(this.theModel, this.localCommitTreePanelView);
        this.remoteCommitTreeModel = new RemoteCommitTreeModel(this.theModel, this.remoteCommitTreePanelView);

        // Add FontAwesome icons to buttons:
        Text openExternallyIcon = GlyphsDude.createIcon(FontAwesomeIcon.FOLDER_OPEN);
        openExternallyIcon.setFill(javafx.scene.paint.Color.WHITE);
        this.openRepoDirButton.setGraphic(openExternallyIcon);
        this.openRepoDirButton.setTooltip(new Tooltip("Open repository directory"));

        Text userIcon = GlyphsDude.createIcon(FontAwesomeIcon.USER);
        userIcon.setFill(Color.WHITE);
        this.switchUserButton.setGraphic(userIcon);

        Text branchIcon = GlyphsDude.createIcon(FontAwesomeIcon.CODE_FORK);
        branchIcon.setFill(Color.WHITE);
        this.branchesButton.setGraphic(branchIcon);

        Text exclamationIcon = GlyphsDude.createIcon(FontAwesomeIcon.EXCLAMATION);
        exclamationIcon.setFill(Color.WHITE);
        this.clearRecentReposButton.setGraphic(exclamationIcon);

        // Buttons start out disabled, since no repo is loaded
        this.setButtonsDisabled(true);

        // Branch selector and trigger button starts invisible, since there's no repo and no branches
        this.branchSelector.setVisible(false);

        this.initializeMenuBar();

        this.theModel.loadRecentRepoHelpersFromStoredPathStrings();
        this.theModel.loadMostRecentRepoHelper();

        this.initPanelViews();
        this.updateUIEnabledStatus();

        RepositoryMonitor.beginWatching(theModel);
        RepositoryMonitor.hasFoundNewChanges.addListener((observable, oldValue, newValue) -> {
            if(newValue) showNewRemoteChangesNotification();
        });
    }

    /**
     * Sets up the layout parameters for things that cannot be set in FXML
     */
    private void initializeLayoutParameters(){
        openRepoDirButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        gitStatusButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        commitButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        mergeFromFetchButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        pushButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        fetchButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        branchesButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);

        gitStatusButton.setMaxWidth(Double.MAX_VALUE);

        workingTreePanelView.setMinSize(Control.USE_PREF_SIZE, 200);
        commitMessageField.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);

        branchSelector.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);

        currentRepoLabel.setMaxWidth(CURRENT_REPO_LABEL_MAX_WIDTH);//- openRepoDirButton.getWidth());

        remoteCommitTreePanelView.heightProperty().addListener((observable, oldValue, newValue) -> {
            remoteCircle.setCenterY(newValue.doubleValue() / 2.0);
            if(oldValue.doubleValue() == 0){
                remoteCircle.setRadius(newValue.doubleValue() / 4.0);
            }
        });

        commitInfoNameCopyButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        commitInfoGoToButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
    }

    /**
     * Gets the local branches and populates the branch selector dropdown.
     *
     * @throws NoRepoLoadedException
     * @throws MissingRepoException
     */
    public void updateBranchDropdown() throws NoRepoLoadedException, MissingRepoException, IOException, GitAPIException {
        RepoHelper currentRepoHelper = this.theModel.getCurrentRepoHelper();
        if(currentRepoHelper==null) throw new NoRepoLoadedException();
        if(!currentRepoHelper.exists()) throw new MissingRepoException();

        List<LocalBranchHelper> branches = currentRepoHelper.callGitForLocalBranches();

        currentRepoHelper.refreshCurrentBranch();
        LocalBranchHelper currentBranch = currentRepoHelper.getCurrentBranch();

        Platform.runLater(() -> {
            this.branchSelector.setVisible(true);
            this.branchSelector.getItems().setAll(branches);
            this.branchSelector.setValue(currentBranch);
        });
    }

    /**
     * Sets up the MenuBar by adding some options to it (for cloning).
     *
     * Each option offers a different way of loading a repository, and each
     * option instantiates the appropriate RepoHelper class for the chosen
     * loading method.
     *
     * Since each option creates a new repo, this method handles errors.
     *
     */
    private void initializeMenuBar() {
        this.newRepoMenu = new Menu("Load New Repository");

        MenuItem cloneOption = new MenuItem("Clone");
        cloneOption.setOnAction(t -> handleLoadRepoMenuItem(new ClonedRepoHelperBuilder(this.theModel)));

        MenuItem existingOption = new MenuItem("Load existing repository");
        existingOption.setOnAction(t -> handleLoadRepoMenuItem(new ExistingRepoHelperBuilder(this.theModel)));

        // TODO: implement New Repository option.
        MenuItem newOption = new MenuItem("Start a new repository");
        newOption.setDisable(true);

        this.newRepoMenu.getItems().addAll(cloneOption, existingOption, newOption);

        // Initialize it with no repos to choose from. This gets updated when there are repos present.
        this.openRecentRepoMenu = new Menu("Open Recent Repository");
        MenuItem noOptionsAvailable = new MenuItem("No recent repositories");
        noOptionsAvailable.setDisable(true);
        this.openRecentRepoMenu.getItems().add(noOptionsAvailable);

        this.menuBar.getMenus().addAll(newRepoMenu, openRecentRepoMenu);

        if (this.theModel.getAllRepoHelpers().size() != 0) {
            // If there are repos from previous sessions, put them in the menu bar
            this.updateMenuBarWithRecentRepos();
        }

    }

    /**
     * Called when a selection is made from the 'Load new Repository' menu. Creates a new repository
     * using the given builder and updates the UI
     * @param builder the builder to use to create a new repository
     */
    private synchronized void handleLoadRepoMenuItem(RepoHelperBuilder builder){
        try{
            RepoHelper repoHelper = builder.getRepoHelperFromDialogs();
            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    try {
                        theModel.openRepoFromHelper(repoHelper);

                        initPanelViews();
                        updateUIEnabledStatus();

                    } catch(BackingStoreException | ClassNotFoundException e) {
                        // These should only occur when the recent repo information
                        // fails to be loaded or stored, respectively
                        // Should be ok to silently fail
                    } catch (MissingRepoException e) {
                        showMissingRepoNotification();
                        updateMenuBarWithRecentRepos();
                    } catch (IOException e) {
                        // Somehow, the repository failed to get properly loaded
                        // TODO: better error message?
                        showRepoWasNotLoadedNotification();
                    }
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Loading existing/cloning repository");
            th.start();
        } catch (IllegalArgumentException e) {
            showInvalidRepoNotification();
            e.printStackTrace();
        } catch(NoOwnerInfoException e) {
            showNotLoggedInNotification(() -> handleLoadRepoMenuItem(builder));
        } catch(JGitInternalException e){
            showNonEmptyFolderNotification();
        } catch(InvalidRemoteException e){
            showInvalidRemoteNotification();
        } catch(TransportException e){
            showNotAuthorizedNotification(() -> handleLoadRepoMenuItem(builder));
        } catch (NoRepoSelectedException e) {
            // The user pressed cancel on the dialog box. Do nothing!
        } catch(IOException | GitAPIException e){
            // Somehow, the repository failed to get properly loaded
            // TODO: better error message?
            showRepoWasNotLoadedNotification();
        }
    }

    /**
     * Puts all the model's RepoHelpers into the menubar.
     */
    private void updateMenuBarWithRecentRepos() {
        Platform.runLater(() -> {
            this.openRecentRepoMenu.getItems().clear();

            List<RepoHelper> repoHelpers = this.theModel.getAllRepoHelpers();
            for(RepoHelper repoHelper : repoHelpers){
                MenuItem recentRepoHelperMenuItem = new MenuItem(repoHelper.toString());
                recentRepoHelperMenuItem.setOnAction(t -> handleRecentRepoMenuItem(repoHelper));
                openRecentRepoMenu.getItems().add(recentRepoHelperMenuItem);
            }

            this.menuBar.getMenus().clear();
            this.menuBar.getMenus().addAll(this.newRepoMenu, this.openRecentRepoMenu);
        });
    }

    /**
     * Called when a selection is made from the 'Open Recent Repository" menu. Loads the repository
     * given and updates the UI
     * @param repoHelper the repository to open
     */
    private synchronized void handleRecentRepoMenuItem(RepoHelper repoHelper){
        Thread th = new Thread(new Task<Void>(){
            @Override
            protected Void call() throws Exception{
                try {
                    theModel.openRepoFromHelper(repoHelper);

                    initPanelViews();
                    updateUIEnabledStatus();
                } catch (IOException e) {
                    // Somehow, the repository failed to get properly loaded
                    // TODO: better error message?
                    showRepoWasNotLoadedNotification();
                } catch(MissingRepoException e){
                    showMissingRepoNotification();
                    updateMenuBarWithRecentRepos();
                } catch (BackingStoreException | ClassNotFoundException e) {
                    // These should only occur when the recent repo information
                    // fails to be loaded or stored, respectively
                    // Should be ok to silently fail
                }
                return null;
            }
        });
        th.setDaemon(true);
        th.setName("Open repository from recent list");
        th.start();
    }

    /**
     * Perform the updateFileStatusInRepo() method for each file whose
     * checkbox is checked. Then commit with the commit message and push.
     */
    public void handleCommitButton() {
        try {
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
            if(!this.theModel.getCurrentRepoHelper().exists()) throw new MissingRepoException();

            String commitMessage = commitMessageField.getText();

            if(!workingTreePanelView.isAnyFileSelected()) throw new NoFilesStagedForCommitException();
            if(commitMessage.length() == 0) throw new NoCommitMessageException();

            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    try{
                        for(RepoFile checkedFile : workingTreePanelView.getCheckedFilesInDirectory()){
                            checkedFile.updateFileStatusInRepo();
                        }

                        theModel.getCurrentRepoHelper().commit(commitMessage);

                        // Now clear the commit text and a view reload ( or `git status`) to show that something happened
                        commitMessageField.clear();
                        onGitStatusButton();
                    } catch(JGitInternalException e){
                        showGenericErrorNotification();
                        e.printStackTrace();
                    } catch(MissingRepoException e){
                        showMissingRepoNotification();
                        setButtonsDisabled(true);
                        updateMenuBarWithRecentRepos();
                    } catch (TransportException e) {
                        showNotAuthorizedNotification(null);
                    } catch (WrongRepositoryStateException e) {
                        System.out.println("Threw a WrongRepositoryStateException");
                        e.printStackTrace();

                        // TODO remove the above debug statements
                        // This should only come up when the user chooses to resolve conflicts in a file.
                        // Do nothing.

                    } catch(GitAPIException | IOException e){
                        // Git error, or error presenting the file chooser window
                        showGenericErrorNotification();
                        e.printStackTrace();
                    }
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git commit");
            th.start();
        } catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        } catch(MissingRepoException e){
            this.showMissingRepoNotification();
            setButtonsDisabled(true);
            updateMenuBarWithRecentRepos();
        } catch(NoCommitMessageException e){
            this.showNoCommitMessageNotification();
        }catch(NoFilesStagedForCommitException e){
            this.showNoFilesStagedForCommitNotification();
        }
    }

    /**
     * Merges in FETCH_HEAD (after a fetch).
     */
    public void handleMergeFromFetchButton() {
        try{
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
            if(!this.theModel.getCurrentRepoHelper().hasUnmergedCommits()) throw new NoCommitsToMergeException();

            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call(){
                    try{
                        if(!theModel.getCurrentRepoHelper().mergeFromFetch()){
                            showUnsuccessfulMergeNotification();
                        }
                        onGitStatusButton();
                    } catch(InvalidRemoteException e){
                        showNoRemoteNotification();
                    } catch(TransportException e){
                        showNotAuthorizedNotification(null);
                    } catch (NoMergeBaseException | JGitInternalException e) {
                        // Merge conflict
                        System.out.println("*****");
                        e.printStackTrace();
                        // todo: figure out rare NoMergeBaseException.
                        //  Has something to do with pushing conflicts.
                        //  At this point in the stack, it's caught as a JGitInternalException.
                    } catch(MissingRepoException e){
                        showMissingRepoNotification();
                        setButtonsDisabled(true);
                        updateMenuBarWithRecentRepos();
                    } catch(GitAPIException | IOException e){
                        showGenericErrorNotification();
                        e.printStackTrace();
                    }
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git merge FETCH_HEAD");
            th.start();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }catch(NoCommitsToMergeException e){
            this.showNoCommitsToMergeNotification();
        }
    }

    /**
     * Performs a `git push`
     */
    public void handlePushButton() {
        try {
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
            if(!this.theModel.getCurrentRepoHelper().hasUnpushedCommits()) throw new NoCommitsToPushException();

            pushButton.setVisible(false);
            pushProgressIndicator.setVisible(true);

            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    try{
                        RepositoryMonitor.resetFoundNewChanges(false);
                        theModel.getCurrentRepoHelper().pushAll();
                        onGitStatusButton();
                    }  catch(InvalidRemoteException e){
                        showNoRemoteNotification();
                    } catch(PushToAheadRemoteError e) {
                        showPushToAheadRemoteNotification();
                    } catch (TransportException e) {
                        if (e.getMessage().contains("git-receive-pack not found")) {
                            // The error has this message if there is no longer a remote to push to
                            showLostRemoteNotification();
                        } else {
                            showNotAuthorizedNotification(null);
                        }
                    } catch(MissingRepoException e){
                        showMissingRepoNotification();
                        setButtonsDisabled(true);
                        updateMenuBarWithRecentRepos();
                    } catch(GitAPIException e){
                        showGenericErrorNotification();
                        e.printStackTrace();
                    }finally{
                        pushProgressIndicator.setVisible(false);
                        pushButton.setVisible(true);
                    }
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git push");
            th.start();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }catch(NoCommitsToPushException e){
            this.showNoCommitsToPushNotification();
        }
    }

    /**
     * Performs a `git fetch`
     */
    public void handleFetchButton(){
        try{
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            fetchButton.setVisible(false);
            fetchProgressIndicator.setVisible(true);

            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    try{
                        RepositoryMonitor.resetFoundNewChanges(false);
                        theModel.getCurrentRepoHelper().fetch();
                        onGitStatusButton();
                    } catch(InvalidRemoteException e){
                        showNoRemoteNotification();
                    } catch (TransportException e) {
                        showNotAuthorizedNotification(null);
                    } catch(MissingRepoException e){
                        showMissingRepoNotification();
                        setButtonsDisabled(true);
                        updateMenuBarWithRecentRepos();
                    } catch(GitAPIException e){
                        showGenericErrorNotification();
                        e.printStackTrace();
                    } finally{
                        fetchProgressIndicator.setVisible(false);
                        fetchButton.setVisible(true);
                    }
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git fetch");
            th.start();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }
    }

    /**
     * Updates the panel views when the "git status" button is clicked.
     *
     * See initPanelViews for Thread information
     */
    public synchronized void onGitStatusButton(){
        Thread th = new Thread(new Task<Void>(){
            @Override
            protected Void call(){
                ReentrantLock lock = new ReentrantLock();
                Condition finishedUpdate = lock.newCondition();

                Platform.runLater(() -> {
                    lock.lock();
                    try{
                        workingTreePanelView.drawDirectoryView();
                        localCommitTreeModel.update();
                        remoteCommitTreeModel.update();

                        finishedUpdate.signal();
                    }catch(GitAPIException | IOException e){
                        showGenericErrorNotification();
                        e.printStackTrace();
                    }finally{
                        lock.unlock();
                    }
                });

                lock.lock();
                try{
                    finishedUpdate.await(); // updateBranchDropdown needs to be called after the trees have
                    updateBranchDropdown(); // been updated, but shouldn't run on the Application thread
                } catch(MissingRepoException e){
                    showMissingRepoNotification();
                    setButtonsDisabled(true);
                    updateMenuBarWithRecentRepos();
                } catch(NoRepoLoadedException e){
                    showNoRepoLoadedNotification();
                    setButtonsDisabled(true);
                } catch(GitAPIException | IOException | InterruptedException e){
                    showGenericErrorNotification();
                    e.printStackTrace();
                }finally{
                    lock.unlock();
                }
                return null;
            }
        });
        th.setDaemon(true);
        th.setName("Git status");
        th.start();
    }

    /**
     * When the circle representing the remote repo is clicked, go to the
     * corresponding remote url
     * @param event the mouse event corresponding to the click
     */
    public void handleRemoteCircleMouseClick(MouseEvent event){
        if(event.getButton() != MouseButton.PRIMARY) return;
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
                if(!this.theModel.getCurrentRepoHelper().exists()) throw new MissingRepoException();

                List<String> remoteURLs = this.theModel.getCurrentRepoHelper().getLinkedRemoteRepoURLs();

                if(remoteURLs.size() == 0){
                    this.showNoRemoteNotification();
                }

                for (String remoteURL : remoteURLs) {
                    if(remoteURL.contains("@")){
                        remoteURL = "https://"+remoteURL.replace(":","/").split("@")[1];
                    }
                    desktop.browse(new URI(remoteURL));
                }
            }catch(URISyntaxException | IOException e){
                this.showGenericErrorNotification();
            }catch(MissingRepoException e){
                this.showMissingRepoNotification();
                this.setButtonsDisabled(true);
                updateMenuBarWithRecentRepos();
            }catch(NoRepoLoadedException e){
                this.showNoRepoLoadedNotification();
                this.setButtonsDisabled(true);
            }
        }
    }

    /**
     * Initializes each panel of the view
     *
     * TODO: change this if/when we update the JDK to 8u60 or higher
     * With JDK version 8u40, creation of control items needs to take place
     * in the application thread even if they are not added to the scene.
     * This is fixed in JDK 8u60 and above
     * https://bugs.openjdk.java.net/browse/JDK-8097541
     *
     * This applies to all methods used here
     */
	private void initPanelViews() {
        Platform.runLater(() -> {
            try{
                workingTreePanelView.drawDirectoryView();
            }catch(GitAPIException e){
                showGenericErrorNotification();
            }
            localCommitTreeModel.init();
            remoteCommitTreeModel.init();
        });

    }

    /**
     * A helper method for enabling/disabling buttons.
     *
     * @param disable a boolean for whether or not to disable the buttons.
     */
    private void setButtonsDisabled(boolean disable) {
        Platform.runLater(() -> {
            openRepoDirButton.setDisable(disable);
            gitStatusButton.setDisable(disable);
            commitButton.setDisable(disable);
            mergeFromFetchButton.setDisable(disable);
            pushButton.setDisable(disable);
            fetchButton.setDisable(disable);
            selectAllButton.setDisable(disable);
            deselectAllButton.setDisable(disable);
            remoteCircle.setVisible(!disable);
            commitMessageField.setDisable(disable);
        });
    }

    /**
     * Checks out the branch that is currently selected in the dropdown.
     */
    public void loadSelectedBranch() {
        LocalBranchHelper selectedBranch = this.branchSelector.getValue();
        if(selectedBranch == null) return;
        Thread th = new Thread(new Task<Void>(){
            @Override
            protected Void call() {

                try{
                    // When a repo is first initialized,the `master` branch is checked-out,
                    //  but it is "unborn" -- it doesn't exist yet in the `refs/heads` folder
                    //  until there are commits.
                    //
                    // (see http://stackoverflow.com/a/21255920/5054197)
                    //
                    // So, check that there are refs in the refs folder (if there aren't, do nothing):
                    String gitDirString = theModel.getCurrentRepo().getDirectory().toString();
                    Path refsHeadsFolder = Paths.get(gitDirString + "/refs/heads");
                    DirectoryStream<Path> pathStream = Files.newDirectoryStream(refsHeadsFolder);
                    Iterator<Path> pathStreamIterator = pathStream.iterator();

                    if (pathStreamIterator.hasNext()){ // => There ARE branch refs in the folder
                        selectedBranch.checkoutBranch();
                        CommitTreeController.focusCommitInGraph(selectedBranch.getHead());
                    }
                }catch(CheckoutConflictException e){
                    showCheckoutConflictsNotification(e.getConflictingPaths());
                    try{
                        updateBranchDropdown();
                    }catch(NoRepoLoadedException e1){
                        showNoRepoLoadedNotification();
                        setButtonsDisabled(true);
                    }catch(MissingRepoException e1){
                        showMissingRepoNotification();
                        setButtonsDisabled(true);
                        updateMenuBarWithRecentRepos();
                    }catch(GitAPIException | IOException e1){
                        showGenericErrorNotification();
                        e1.printStackTrace();
                    }
                }catch(GitAPIException | IOException e){
                    showGenericErrorNotification();
                    e.printStackTrace();
                }
                return null;
            }
        });
        th.setDaemon(true);
        th.setName("Branch Checkout");
        th.start();
    }

    /**
     * A helper helper method to enable or disable buttons/UI elements
     * depending on whether there is a repo open for the buttons to
     * interact with.
     */
    private void updateUIEnabledStatus() {
        try{
            if(this.theModel.getCurrentRepoHelper() == null && this.theModel.getAllRepoHelpers().size() == 0) {
                // (There's no repo for the buttons to interact with)
                setButtonsDisabled(true);
                Platform.runLater(() -> this.branchSelector.setVisible(false));
            } else if (this.theModel.getCurrentRepoHelper() == null && this.theModel.getAllRepoHelpers().size() > 0) {
                // (There's no repo for buttons to interact with, but there are repos in the menu bar)
                setButtonsDisabled(true);
                Platform.runLater(() -> this.branchSelector.setVisible(false));
                this.updateMenuBarWithRecentRepos();
            }else{
                setButtonsDisabled(false);
                this.updateBranchDropdown();
                this.updateMenuBarWithRecentRepos();
                this.updateCurrentRepoLabel();
            }
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }catch(MissingRepoException e){
            this.showMissingRepoNotification();
            setButtonsDisabled(true);
            updateMenuBarWithRecentRepos();
        } catch (GitAPIException | IOException e) {
            this.showGenericErrorNotification();
            e.printStackTrace();
        }
    }

    /**
     * Updates the repo label with the current repo's directory name
     */
    private void updateCurrentRepoLabel() {
        String name = this.theModel.getCurrentRepoHelper().toString();
        Platform.runLater(() -> this.currentRepoLabel.setText(name));
    }

    /**
     * Clears the history stored with the Preferences API.
     *
     * TODO: Come up with better solution?
     *
     * @throws BackingStoreException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void clearSavedStuff() throws BackingStoreException, IOException, ClassNotFoundException {
        this.theModel.clearStoredPreferences();
        this.showPrefsClearedNotification();
    }

    /**
     * Creates a new owner and set it as the current default owner.
     */
    public boolean switchUser() {
        // Begin with a nullified RepoOwner:
        RepoOwner newOwner = this.theModel.getDefaultOwner() == null ? new RepoOwner(null, null) : this.theModel.getDefaultOwner();
        boolean switchedLogin = true;

        try {
            newOwner = new RepoOwner();
        } catch (CancelledLoginException e) {
            // User cancelled the login, so we'll leave the owner full of nullness.
            switchedLogin = false;
        }

        RepoHelper currentRepoHelper = theModel.getCurrentRepoHelper();
        if(currentRepoHelper != null){
            currentRepoHelper.setOwner(newOwner);
        }
        this.theModel.setCurrentDefaultOwner(newOwner);
        return switchedLogin;
    }

    /**
     * Called when the switch user button is clicked. See switchUser
     */
    public void handleSwitchUserButton(){
        this.switchUser();
    }

    /**
     * Opens the current repo directory (e.g. in Finder or Windows Explorer).
     */
    public void openRepoDirectory(){
        if (Desktop.isDesktopSupported()) {
            try{
                if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
                Desktop.getDesktop().open(this.theModel.getCurrentRepoHelper().localPath.toFile());
            }catch(IOException | IllegalArgumentException e){
                this.showFailedToOpenLocalNotification();
            }catch(NoRepoLoadedException e){
                this.showNoRepoLoadedNotification();
                setButtonsDisabled(true);
            }
        }
    }

    /// BEGIN: ERROR NOTIFICATIONS:

    private void showNotLoggedInNotification(Runnable callBack) {
        Platform.runLater(() -> {
            this.notificationPane.setText("You need to log in to do that.");

            Action loginAction = new Action("Enter login info", e -> {
                this.notificationPane.hide();
                if(this.switchUser()){
                    if(callBack != null) callBack.run();
                }
            });

            this.notificationPane.getActions().clear();
            this.notificationPane.getActions().setAll(loginAction);
            this.notificationPane.show();
        });
    }


    private void showNoRepoLoadedNotification() {
        Platform.runLater(() -> {
            this.notificationPane.setText("You need to load a repository before you can perform operations on it!");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showInvalidRepoNotification() {
        Platform.runLater(()-> {
            this.notificationPane.setText("Make sure the directory you selected contains an existing (non-bare) Git repository.");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showMissingRepoNotification(){
        Platform.runLater(()-> {
            this.notificationPane.setText("That repository no longer exists.");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showNoRemoteNotification(){
        Platform.runLater(()-> {
            String name = this.theModel.getCurrentRepoHelper() != null ? this.theModel.getCurrentRepoHelper().toString() : "the current repository";

            this.notificationPane.setText("There is no remote repository associated with " + name);

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showFailedToOpenLocalNotification(){
        Platform.runLater(()-> {
            String path = this.theModel.getCurrentRepoHelper() != null ? this.theModel.getCurrentRepoHelper().getLocalPath().toString() : "the location of the local repository";

            this.notificationPane.setText("Could not open directory at " + path);

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showNonEmptyFolderNotification() {
        Platform.runLater(()-> {
            this.notificationPane.setText("Make sure the directory you selected is completely empty. The best " +
                    "way to do this is to create a new folder from the directory chooser.");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showInvalidRemoteNotification() {
        Platform.runLater(()-> {
            this.notificationPane.setText("Make sure you entered the correct remote URL.");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showGenericErrorNotification() {
        Platform.runLater(()-> {
            this.notificationPane.setText("Sorry, there was an error.");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showNotAuthorizedNotification(Runnable callback) {
        Platform.runLater(() -> {
            this.notificationPane.setText("The login information you gave does not allow you to modify this repository. Try switching your login and trying again.");

            Action loginAction = new Action("Log in", e -> {
                this.notificationPane.hide();
                if(this.switchUser()){
                    if(callback != null) callback.run();
                }
            });

            this.notificationPane.getActions().clear();
            this.notificationPane.getActions().setAll(loginAction);
            this.notificationPane.show();
        });
    }

    private void showRepoWasNotLoadedNotification() {
        Platform.runLater(()-> {
            this.notificationPane.setText("No repository was loaded.");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showPrefsClearedNotification() {
        Platform.runLater(()-> {
            this.notificationPane.setText("Your recent repositories have been cleared. Restart the app for changes to take effect.");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showCheckoutConflictsNotification(List<String> conflictingPaths) {
        Platform.runLater(() -> {
            String conflictList = "";
            for(String pathName : conflictingPaths){
                conflictList += "\n" + pathName;
            }
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Conflicting files");
            alert.setHeaderText("Can't checkout that branch");
            alert.setContentText("You can't switch to that branch because of the following conflicting files between that branch and your current branch: "
                    + conflictList);

            this.notificationPane.setText("You can't switch to that branch because there would be a merge conflict. Stash your changes or resolve conflicts first.");

            Action seeConflictsAction = new Action("See conflicts", e -> {
                this.notificationPane.hide();
                alert.showAndWait();
            });

            this.notificationPane.getActions().clear();
            this.notificationPane.getActions().setAll(seeConflictsAction);

            this.notificationPane.show();
        });
    }


    private void showPushToAheadRemoteNotification(){
        Platform.runLater(() -> {
            this.notificationPane.setText("The remote repository is ahead of the local. You need to fetch and then merge (pull) before pushing.");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showLostRemoteNotification() {
        Platform.runLater(() -> {
            this.notificationPane.setText("The push failed because the remote repository couldn't be found.");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showUnsuccessfulMergeNotification(){
        Platform.runLater(() -> {
            this.notificationPane.setText("Merging failed");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showNewRemoteChangesNotification(){
        Platform.runLater(() -> {
            this.notificationPane.setText("There are new changes in the remote repository.");

            Action fetchAction = new Action("Fetch", e -> {
                this.notificationPane.hide();
                handleFetchButton();
            });

            Action ignoreAction = new Action("Ignore", e -> {
                this.notificationPane.hide();
                RepositoryMonitor.resetFoundNewChanges(true);
            });

            this.notificationPane.getActions().clear();
            this.notificationPane.getActions().setAll(fetchAction, ignoreAction);

            this.notificationPane.show();
        });
    }

    private void showNoFilesStagedForCommitNotification(){
        Platform.runLater(() -> {
            this.notificationPane.setText("You need to select which files to commit");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showNoCommitMessageNotification(){
        Platform.runLater(() -> {
            this.notificationPane.setText("You need to write a commit message in order to commit your changes");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showNoCommitsToPushNotification(){
        Platform.runLater(() -> {
            this.notificationPane.setText("There aren't any local commits to push");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    private void showNoCommitsToMergeNotification(){
        Platform.runLater(() -> {
            this.notificationPane.setText("There aren't any fetched commits to merge");

            this.notificationPane.getActions().clear();
            this.notificationPane.show();
        });
    }

    // END: ERROR NOTIFICATIONS ^^^

    /**
     * Opens up the current repo helper's Branch Manager window after
     * passing in this SessionController object, so that the
     * BranchManagerController can update the main window's views.
     */
    public void showBranchManager() {
        try{
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
//            BranchManagerController branchManagerController = this.theModel.getCurrentRepoHelper().showBranchManagerWindow();
//            BranchManagerModel branchManagerModel = this.theModel.getCurrentRepoHelper().getBranchManagerModel();
//            branchManagerModel.setSessionControllerContext(this);
//            branchManagerController.showBranchChooserWindow();
            // TODO: Set session controller context for the Branchmanagercontroller
            this.theModel.getCurrentRepoHelper().showBranchManagerWindow();
        }catch(IOException e){
            this.showGenericErrorNotification();
            e.printStackTrace();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }
    }

    /**
     * Displays information about the commit with the given id
     * @param id the selected commit
     */
    public void selectCommit(String id){
        CommitHelper commit = this.theModel.getCurrentRepoHelper().getCommit(id);
        commitInfoNameText.setText(commit.getName());
        commitInfoAuthorText.setText(commit.getAuthorName());
        commitInfoDateText.setText(commit.getFormattedWhen());
        commitInfoNameCopyButton.setDisable(false);
        commitInfoGoToButton.setDisable(false);

        String s = "";
        for(BranchHelper branch : commit.getBranchesAsHead()){
            if(branch instanceof RemoteBranchHelper){
                s = s + "origin/";
            }
            s = s + branch.getBranchName() + "\n";
        }
        if(s.length() > 0){
            commitInfoMessageText.setText("Head of branches: \n"+s+"\n\n"+commit.getMessage(true));
        }else{
            commitInfoMessageText.setText(commit.getMessage(true));
        }
    }

    /**
     * Stops displaying commit information
     */
    public void clearSelectedCommit(){
        commitInfoNameText.clear();
        commitInfoAuthorText.setText("");
        commitInfoDateText.setText("");
        commitInfoMessageText.clear();
        commitInfoNameCopyButton.setDisable(true);
        commitInfoGoToButton.setDisable(true);
    }

    /**
     * Copies the commit hash onto the clipboard
     */
    public void handleCommitNameCopyButton(){
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(commitInfoNameText.getText());
        clipboard.setContent(content);
    }

    /**
     * Jumps to the selected commit in the tree display
     */
    public void handleGoToCommitButton(){
        String id = commitInfoNameText.getText();
        CommitTreeController.focusCommitInGraph(id);
    }

    /**
     * Selects all files in the working tree for a commit.
     *
     */
    public void onSelectAllButton() {
        this.workingTreePanelView.setAllFilesSelected(true);
    }

    /**
     * Deselects all files in the working tree for a commit.
     *
     */
    public void onDeselectAllButton() {
        this.workingTreePanelView.setAllFilesSelected(false);
    }
}