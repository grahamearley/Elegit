package elegit.monitors;

import elegit.models.RepoHelper;
import elegit.models.ThreadsafeGitManager;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import javafx.concurrent.Task;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Class used to watch a conflictingRepoFile to see if it's been modified
 * after the user has been informed that the file was conflicting
 */

public class ConflictingFileWatcher {

    // list of files that were modified after the user was informed they were conflicting
    private static final Set<String> conflictingThenModifiedFiles = ConcurrentHashMap.newKeySet();
    private static final Set<String> conflictingFiles = ConcurrentHashMap.newKeySet();
    private static final long CONFLICT_CHECK_INTERVAL = 1000;

    /**
     * returns a list of the files that were conflicting and then recently modified
     * @return ArrayList<String>
     */
    public static Set<String> getConflictingThenModifiedFiles() {
        return Collections.unmodifiableSet(conflictingThenModifiedFiles);
    }

    /**
     * removes the given file from the list
     * @param fileToRemove String
     */
    public static void removeFile(String fileToRemove) {
        conflictingThenModifiedFiles.remove(fileToRemove);
    }

    /**
     * Spins off a new thread to watch the directories that contain conflicting files
     *
     * @param currentRepo RepoHelper
     * @throws GitAPIException
     * @throws IOException
     */
    public static void watchConflictingFiles(RepoHelper currentRepo) {

        if(currentRepo == null) return;

        Observable.fromCallable(new Callable<Boolean>() {

            @Override
            public Boolean call() throws IOException, GitAPIException {
                // gets the conflicting files
                AtomicReference<ThreadsafeGitManager> threadsafeGitManager = currentRepo.getThreadsafeGitManager();
                Set<String> newConflictingFiles = threadsafeGitManager.get().getConflicting(threadsafeGitManager.get().getStatus());
                for (String newFile : newConflictingFiles) {
                    if (!conflictingFiles.contains(newFile)) {
                        conflictingFiles.add(newFile);
                    }
                }
                // removes files that aren't conflicting anymore from conflictingThenModifiedFiles
                for (String marked : conflictingThenModifiedFiles) {
                    if (!conflictingFiles.contains(marked)) {
                        conflictingThenModifiedFiles.remove(marked);
                    }
                }

                // gets the path to the repo directory
                Path directory = (new File(currentRepo.getRepo().getDirectory().getParent())).toPath();

                // for each conflicting file, watch its parent directory
                for (String fileToWatch : conflictingFiles) {
                    Path fileToWatchPath = directory.resolve((new File(fileToWatch)).toPath()).getParent();
                    watch(fileToWatchPath, fileToWatch);
                }
                return true;
            }

            /**
             * Spins off a new thread to watch each directory
             *
             * @param directoryToWatch Path
             * @throws IOException
             */
            private void watch(Path directoryToWatch, String fileToWatch)  {

                Observable.fromCallable(() -> {
                    // creates a WatchService
                    WatchService watcher = FileSystems.getDefault().newWatchService();
                    WatchKey key = directoryToWatch.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);

                    // while the file is conflicting, check to see if its been modified
                    while (conflictingFiles.contains(fileToWatch)) {
                        List<WatchEvent<?>> events = key.pollEvents();
                        for (WatchEvent<?> event : events) {

                            // if a conflicting file was modified, remove it from conflictingFiles and add it to conflictingThenModifiedFiles
                            String path = event.context().toString();
                            Path tmp = (new File(fileToWatch)).toPath();
                            // the path in conflictingFiles is either the file name itself or a path that ends with the file name
                            if (tmp.endsWith(path) || tmp.toString().equals(path)) {
                                conflictingFiles.remove(tmp.toString());
                                conflictingThenModifiedFiles.add(tmp.toString());
                            }
                        }
                        boolean valid = key.reset();
                        if (!valid) {
                            break;
                        }

                        try {
                            Thread.sleep(CONFLICT_CHECK_INTERVAL);
                        } catch (InterruptedException e) {
                            // TODO: SOMETHING REASONABLE
                        }

                    }
                    return true;
                }).subscribeOn(Schedulers.io())
                  .subscribe();
            }
        })
        .subscribeOn(Schedulers.io())
        .subscribe();
    }
}
