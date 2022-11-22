package org.openlca.app.editors.graphical.model.commands;

import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.commands.Command;
import org.eclipse.osgi.util.NLS;
import org.openlca.app.M;
import org.openlca.app.editors.graphical.GraphConfig;
import org.openlca.app.editors.graphical.GraphEditor;
import org.openlca.app.editors.graphical.GraphFile;
import org.openlca.app.editors.graphical.actions.GraphActionIds;
import org.openlca.app.editors.graphical.model.Graph;
import org.openlca.jsonld.Json;

public class EditConfigCommand extends Command {

	private final GraphEditor editor;
	private final GraphicalViewer viewer;
	private final GraphConfig oldConfig;
	private final GraphConfig newConfig;
	/** Stores the new graph. */
	private Graph newGraph;
	/** Stores the old graph. */
	private final Graph oldGraph;

	/**
	 * Create a command that can reset the Graph model to use the new config.
	 */
	public EditConfigCommand(Graph graph, GraphConfig newConfig) {
		if (graph == null) {
			throw new IllegalArgumentException();
		}
		this.editor = graph.editor;
		this.viewer = (GraphicalViewer) editor.getAdapter(GraphicalViewer.class);
		this.oldGraph = graph;
		this.oldConfig = graph.getConfig();
		this.newConfig = newConfig;

		setLabel(NLS.bind(M.Edit, M.Settings));
	}

	@Override
	public boolean canExecute() {
		return true;
	}

	@Override
	public void execute() {
		newConfig.copyTo(editor.config);
		// Create new nodes with the new config.
		var rootObj = GraphFile.createJsonArray(editor, editor.getModel());
		var nodeArray = Json.getArray(rootObj, "nodes");
		var stickyNoteArray = Json.getArray(rootObj, "sticky-notes");
		newGraph = editor.getGraphFactory().createGraph(editor, nodeArray,
				stickyNoteArray);

		editor.setModel(newGraph);
		viewer.setContents(newGraph);

		editor.setDirty();

		// Update the status of the config related action.
		var registry = editor.getActionRegistry();
		var action = registry.getAction(GraphActionIds.SHOW_ELEMENTARY_FLOWS);
		action.setChecked(editor.config.showElementaryFlows());
	}

	@Override
	public void redo() {
		newConfig.copyTo(editor.config);
		editor.setModel(newGraph);
		viewer.setContents(newGraph);

		editor.setDirty();
	}

	@Override
	public void undo() {
		oldConfig.copyTo(editor.config);
		editor.setModel(oldGraph);
		viewer.setContents(oldGraph);

		editor.setDirty();
	}

}
