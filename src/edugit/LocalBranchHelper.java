package edugit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Arrays;

/**
 * Created by grahamearley on 6/23/15.
 */
public class LocalBranchHelper extends BranchHelper {
    public LocalBranchHelper(String refPathString, Repository repo) throws IOException {
        super(refPathString, repo);
//        BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(this.repo, this.branchName);
//
//        if (trackingStatus != null) {
//            String trackedBranchRefPath = trackingStatus.getRemoteTrackingBranch();
//
//            RemoteBranchHelper trackedBranch = RemoteBranchHelper.getRemoteBranchHelperByRefPath(trackedBranchRefPath, this.repo);
//            trackedBranch.setTrackingBranch(this);
//        }
    }

    public LocalBranchHelper(Ref branchRef, Repository repo) throws IOException {
        this(branchRef.getName(), repo);
    }

    @Override
    public String getBranchName() {
        String[] slashSplit = this.refPathString.split("/");
        if (slashSplit.length >= 2) {

            /*
            Local branches are stored in the .git directory like this:
            `refs/heads/BRANCH_NAME`.

            For example:
            `refs/heads/master`.
    (index): 0    1     2

            We want to cut out the `refs/remotes/origin/` part to get at the branch name.
            This means cutting the first two parts of the array, split at the '/' char.
            */

            String[] removedFirstTwoDirectoriesInPath = Arrays.copyOfRange(slashSplit, 2, slashSplit.length);

            // Now rejoin at the '/' key, which we split at earlier (in case there is a slash in the branch
            //   name or something):
            String branchName = String.join("/", removedFirstTwoDirectoriesInPath);
            return branchName;

        } else {

            /*
            However, if we're getting a ref that's not nested in the directory, like HEAD or FETCH_HEAD,
            we just want to return the original string.
             */

            return this.refPathString;

        }
    }

    @Override
    public void checkoutBranch() throws GitAPIException {
        new Git(this.repo).checkout().setName(this.branchName).call();
    }

    @Override
    public String toString() {
        return "LOCAL:" + super.toString();
    }
}