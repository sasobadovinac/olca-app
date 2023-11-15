package org.openlca.app.collaboration.navigation;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.swt.graphics.Image;
import org.openlca.app.collaboration.navigation.NavElement.ElementType;
import org.openlca.app.db.Database;
import org.openlca.app.db.Repository;
import org.openlca.app.navigation.elements.CategoryElement;
import org.openlca.app.navigation.elements.DatabaseElement;
import org.openlca.app.navigation.elements.INavigationElement;
import org.openlca.app.navigation.elements.LibraryDirElement;
import org.openlca.app.navigation.elements.LibraryElement;
import org.openlca.app.navigation.elements.ModelElement;
import org.openlca.app.navigation.elements.NavigationRoot;
import org.openlca.app.rcp.images.Images;
import org.openlca.app.rcp.images.Overlay;
import org.openlca.core.database.config.DatabaseConfig;
import org.openlca.core.model.Category;
import org.openlca.core.model.descriptors.RootDescriptor;
import org.openlca.git.GitIndex;
import org.openlca.git.util.Constants;
import org.openlca.git.util.GitUtil;
import org.openlca.git.util.Repositories;
import org.openlca.jsonld.LibraryLink;
import org.openlca.util.Strings;

public class RepositoryLabel {

	public static final String CHANGED_STATE = "> ";

	public static Image getWithOverlay(INavigationElement<?> elem) {
		if (Database.get() == null || !Repository.isConnected())
			return null;
		if (elem instanceof ModelElement e
				&& !e.getLibrary().isPresent()
				&& !e.isFromLibrary()
				&& isNew(NavRoot.get(e)))
			return Images.get(e.getContent(), Overlay.ADDED);
		if (elem instanceof LibraryElement e
				&& e.getDatabase().isPresent()
				&& isNew(NavRoot.get(e)))
			return Images.library(Overlay.ADDED);
		if (elem instanceof CategoryElement e
				&& isNew(NavRoot.get(e)))
			return Images.get(e.getContent(), Overlay.ADDED);
		return null;
	}

	public static String getRepositoryText(DatabaseConfig dbConfig) {
		if (!Database.isActive(dbConfig) || !Repository.isConnected())
			return null;
		var repo = Repository.get();
		var ahead = repo.localHistory.getAheadOf(Constants.REMOTE_REF);
		var behind = repo.localHistory.getBehindOf(Constants.REMOTE_REF);
		var user = repo.user();
		var text = " [";
		if (!Strings.nullOrEmpty(user)) {
			text += user + "@";
		}
		if (repo.client != null) {
			text += repo.client.serverUrl + "/" + repo.client.repositoryId;
		} else {
			text += "local";
		}
		if (!ahead.isEmpty()) {
			text += " \u2191" + ahead.size();
		}
		if (!behind.isEmpty()) {
			text += " \u2193" + behind.size();
		}
		return text + "]";
	}

	public static String getStateIndicator(INavigationElement<?> elem) {
		if (Database.get() == null || !Repository.isConnected() || elem == null || elem.getLibrary().isPresent())
			return null;
		if (elem instanceof DatabaseElement e && !Database.isActive(e.getContent()))
			return null;
		if (elem instanceof LibraryDirElement e && elem.getParent() instanceof NavigationRoot)
			return null;
		if (elem instanceof LibraryElement e && e.getDatabase() == null)
			return null;
		if (!hasChanged(NavRoot.get(elem)))
			return null;
		return CHANGED_STATE;
	}

	public static boolean hasChanged(INavigationElement<?> elem) {
		if (Database.get() == null || !Repository.isConnected() || elem == null || elem.getLibrary().isPresent())
			return false;
		return hasChanged(NavRoot.get(elem));
	}

	public static boolean hasChanged(NavElement elem) {
		if (Database.get() == null || !Repository.isConnected() || elem == null || elem.isFromLibrary())
			return false;
		if (elem.is(ElementType.MODEL)) {
			if (isNew(elem))
				return false;
			var d = (RootDescriptor) elem.content();
			var entry = index().get(NavRoot.get().categoryPaths, d);
			return d.lastChange != entry.lastChange()
					|| d.version != entry.version();
		}
		if (elem.is(ElementType.DATABASE) && librariesChanged())
			return true;
		if (elem.is(ElementType.LIBRARY_DIR))
			return librariesChanged();
		for (var child : elem.children())
			if (hasChanged(child) || (child.is(ElementType.MODEL, ElementType.CATEGORY) && isNew(child)))
				return true;
		return containsDeleted(elem);
	}

	private static boolean isNew(NavElement elem) {
		if (elem == null || elem.isFromLibrary())
			return false;
		if (elem.is(ElementType.LIBRARY) && isNewLibrary((String) elem.content()))
			return true;
		if (elem.is(ElementType.MODEL) && !index().has(NavRoot.get().categoryPaths, (RootDescriptor) elem.content()))
			return true;
		if (elem.is(ElementType.CATEGORY) && !index().has((Category) elem.content()))
			return true;
		return false;
	}

	private static boolean containsDeleted(NavElement elem) {
		if (elem.is(ElementType.MODEL))
			return false;
		for (var child : elem.children())
			if (containsDeleted(child))
				return true;
		if (!elem.is(ElementType.MODEL_TYPE, ElementType.CATEGORY))
			return false;
		var fromIndex = index().getSubPaths(elem.getPath(index()))
				.stream().filter(Predicate.not(GitUtil::isBinDir))
				.toList();
		var fromNavigation = elem.children()
				.stream().map(e -> e.getPath(index()))
				.collect(Collectors.toSet());
		for (var entry : fromIndex)
			if (!fromNavigation.contains(entry))
				return true;
		return false;
	}

	private static boolean librariesChanged() {
		var info = Repositories.infoOf(Repository.get().git);
		var libsBefore = info == null ? new ArrayList<LibraryLink>() : info.libraries();
		var libsNow = LibraryLink.of(Database.get().getLibraries());
		if (libsBefore.size() != libsNow.size())
			return true;
		for (var lib : libsBefore)
			if (!libsNow.contains(lib))
				return true;
		for (var lib : libsNow)
			if (!libsBefore.contains(lib))
				return true;
		return false;
	}

	private static boolean isNewLibrary(String lib) {
		var info = Repositories.infoOf(Repository.get().git);
		var libsBefore = info == null ? new ArrayList<LibraryLink>() : info.libraries();
		return !libsBefore.contains(new LibraryLink(lib, null));
	}

	private static GitIndex index() {
		return Repository.get().gitIndex;
	}

}
