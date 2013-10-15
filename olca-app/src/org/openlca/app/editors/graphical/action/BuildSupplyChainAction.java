package org.openlca.app.editors.graphical.action;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.PartInitException;
import org.openlca.app.Messages;
import org.openlca.app.components.ProgressAdapter;
import org.openlca.app.db.Cache;
import org.openlca.app.editors.graphical.GraphicalEditorInput;
import org.openlca.app.editors.graphical.ProductSystemGraphEditor;
import org.openlca.app.editors.graphical.model.ProcessNode;
import org.openlca.app.util.Editors;
import org.openlca.app.util.Labels;
import org.openlca.app.util.UI;
import org.openlca.core.database.IProductSystemBuilder;
import org.openlca.core.matrix.LongPair;
import org.openlca.core.model.ProcessType;
import org.openlca.core.model.ProductSystem;
import org.openlca.core.model.descriptors.Descriptors;
import org.openlca.core.model.descriptors.ProductSystemDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildSupplyChainAction extends Action {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private ProcessNode node;
	private ProcessType preferredType;

	BuildSupplyChainAction(ProcessType preferredType) {
		setId(ActionIds.BUILD_SUPPLY_CHAIN);
		String processType = Labels.processType(preferredType);
		setText(NLS.bind(Messages.Systems_Prefer, processType));
		this.preferredType = preferredType;
	}

	void setNode(ProcessNode node) {
		this.node = node;
	}

	@Override
	public void run() {
		try {
			new ProgressMonitorDialog(UI.shell())
					.run(true, false, new Runner());
		} catch (final Exception e) {
			log.error("Failed to complete product system. ", e);
		}
		reloadEditor();
	}

	private void reloadEditor() {
		ProductSystemDescriptor descriptor = Descriptors.toDescriptor(node
				.getParent().getProductSystem());
		GraphicalEditorInput input = new GraphicalEditorInput(descriptor);
		for (IEditorReference ref : Editors.getReferences())
			try {
				if (input.equals(ref.getEditorInput())) {
					Editors.close(ref);
					break;
				}
			} catch (PartInitException e) {
				log.error("Error closing editor", e);
			}
		Editors.open(input, ProductSystemGraphEditor.ID);
	}

	private class Runner implements IRunnableWithProgress {

		@Override
		public void run(IProgressMonitor monitor)
				throws InvocationTargetException, InterruptedException {
			node.getParent().getEditor().doSave(null);
			monitor.beginTask(Messages.Systems_CreatingProductSystem,
					IProgressMonitor.UNKNOWN);
			ProductSystem system = node.getParent().getProductSystem();
			ProgressAdapter progress = new ProgressAdapter(monitor);
			LongPair idPair = new LongPair(node.getProcess().getId(), node
					.getProcess().getQuantitativeReference());
			IProductSystemBuilder.Factory.create(Cache.getMatrixCache(),
					progress, preferredType == ProcessType.LCI_RESULT)
					.autoComplete(system, idPair);
			progress.done();
		}
	}

}
