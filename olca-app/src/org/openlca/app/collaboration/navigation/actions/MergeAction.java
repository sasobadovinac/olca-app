package org.openlca.app.collaboration.navigation.actions;

import java.io.IOException;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.openlca.app.M;
import org.openlca.app.db.Cache;
import org.openlca.app.db.Database;
import org.openlca.app.db.Repository;
import org.openlca.app.navigation.actions.INavigationAction;
import org.openlca.app.navigation.elements.INavigationElement;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.util.MsgBox;
import org.openlca.git.actions.GitMerge;
import org.openlca.git.find.Commits;
import org.openlca.git.util.Constants;

public class MergeAction extends Action implements INavigationAction {

	@Override
	public String getText() {
		return M.Merge + "...";
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return Icon.MERGE.descriptor();
	}

	@Override
	public boolean isEnabled() {
		return !Repository.get().history.getBehind().isEmpty();
	}

	@Override
	public void run() {
		Database.getWorkspaceIdUpdater().disable();
		var repo = Repository.get();
		var commits = Commits.of(repo.git);
		try {
			if (!Actions.getWorkspaceChanges().isEmpty()) {
				// TODO allow if not conflicting
				// TODO offer different solutions (e.g. stash, discard, commit)
				MsgBox.info("You can only merge into an unchanged database, please stash your changes first");
			}
			var remoteCommit = commits.get(commits.resolve(Constants.REMOTE_BRANCH));
			var conflictResolutionMap = Conflicts.identifyAndSolve(remoteCommit);
			if (conflictResolutionMap == null)
				return;
			var changed = GitMerge
					.from(repo.git)
					.into(Database.get())
					.update(repo.workspaceIds)
					.as(repo.personIdent())
					.resolveConflictsWith(conflictResolutionMap)
					.run();
			if (!changed) {
				MsgBox.info("No changes to merge");
				return;
			}
		} catch (IOException | GitAPIException e) {
			Actions.handleException("Error during git merge", e);
		} finally {
			Database.getWorkspaceIdUpdater().enable();
			Cache.evictAll();
			Actions.refresh();
		}
	}

	@Override
	public boolean accept(List<INavigationElement<?>> elements) {
		if (!Repository.isConnected())
			return false;
		return true;
	}

}
