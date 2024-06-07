package org.openlca.app.editors.graphical.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.gef.ui.actions.WorkbenchPartAction;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.forms.FormDialog;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.openlca.app.App;
import org.openlca.app.M;
import org.openlca.app.db.Database;
import org.openlca.app.editors.graphical.GraphEditor;
import org.openlca.app.editors.graphical.GraphFile;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.util.Controls;
import org.openlca.app.util.ErrorReporter;
import org.openlca.app.util.Labels;
import org.openlca.app.util.Question;
import org.openlca.app.util.UI;
import org.openlca.core.matrix.linking.ProviderLinking;
import org.openlca.core.model.ProcessType;

public class LinkUpdateAction extends WorkbenchPartAction {

	private final GraphEditor editor;

	public LinkUpdateAction(GraphEditor part) {
		super(part);
		editor = part;
		setId(GraphActionIds.LINK_UPDATE);
		setImageDescriptor(Icon.UPDATE.descriptor());
		setText(M.UpdateProcessLinks);
	}

	@Override
	protected boolean calculateEnabled() {
		return true;
	}

	@Override
	public void run() {
		if (editor == null)
			return;
		var dialog = new Dialog(editor);
		if (dialog.open() != Window.OK)
			return;
		boolean doIt = Question.ask(M.ExecuteUpdateQ, M.ExecuteUpdateQuestion);
		if (!doIt)
			return;

		var system = editor.getProductSystem();
		var progress = new ProgressMonitorDialog(UI.shell());
		try {
			progress.run(false, false, editor.getProductSystemEditor()::doSave);
		} catch (Exception e) {
			ErrorReporter.on("failed to save product system in link update", e);
			return;
		}

		App.close(system);
		GraphFile.clear(editor);
		App.runWithProgress(M.UpdateLinks, () -> {
			try {
				var productSystem = dialog.config.execute();
				App.open(productSystem);
			} catch (Exception e) {
				ErrorReporter.on("Update failed", e);
			}
		});
	}

	private static class Dialog extends FormDialog {

		private final LinkUpdate.Config config;
		private final List<Consumer<ProviderLinking>> onLinkingChanged;

		Dialog(GraphEditor editor) {
			super(UI.shell());
			onLinkingChanged = new ArrayList<>();
			setBlockOnOpen(true);
			config = LinkUpdate.of(Database.get(), editor.getProductSystem());
		}

		@Override
		protected Point getInitialSize() {
			return UI.initialSizeOf(this, 600, 350);
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			if (newShell != null) {
				newShell.setText(M.UpdateLinks);
			}
		}

		@Override
		protected void createFormContent(IManagedForm form) {
			var tk = form.getToolkit();
			var body = UI.dialogBody(form.getForm(), tk);
			UI.gridLayout(body, 2);
			createProviderCombo(tk, body);
			createTypeCombo(tk, body);
			createKeepExistingCheck(tk, body);
			createLocationCheck(tk, body);
		}

		private void createProviderCombo(FormToolkit tk, Composite body) {
			var options = ProviderLinking.values();
			var items = new String[options.length];
			int selected = -1;
			for (int i = 0; i < items.length; i++) {
				var option = options[i];
				items[i] = Labels.of(option);
				if (option == ProviderLinking.PREFER_DEFAULTS) {
					selected = i;
				}
			}
			var combo = UI.labeledCombo(body, tk, M.ProviderSelection);
			combo.setItems(items);
			combo.select(selected);
			config.withProviderLinking(options[selected]);
			Controls.onSelect(combo, $ -> {
				var option = options[combo.getSelectionIndex()];
				config.withProviderLinking(option);
				for (var c : onLinkingChanged) {
					c.accept(option);
				}
			});
		}

		private void createTypeCombo(FormToolkit tk, Composite body) {
			String[] items = {
					Labels.of(ProcessType.UNIT_PROCESS),
					Labels.of(ProcessType.LCI_RESULT) };
			var combo = UI.labeledCombo(body, tk, M.PreferredProcessType);
			combo.setItems(items);
			combo.select(1);
			config.withPreferredType(ProcessType.LCI_RESULT);
			Controls.onSelect(combo, $ -> config.withPreferredType(combo.getSelectionIndex() == 0
					? ProcessType.UNIT_PROCESS
					: ProcessType.LCI_RESULT));
			onLinkingChanged.add(linking -> combo.setEnabled(linking != ProviderLinking.ONLY_DEFAULTS));
		}

		private void createKeepExistingCheck(FormToolkit tk, Composite body) {
			UI.filler(body, tk);
			var check = tk.createButton(body, M.KeepAllExistingLinks, SWT.CHECK);
			config.keepExistingLinks(true);
			check.setSelection(true);
			Controls.onSelect(
					check, $ -> config.keepExistingLinks(check.getSelection()));
		}

		private void createLocationCheck(FormToolkit tk, Composite body) {
			UI.filler(body, tk);
			var check = tk.createButton(
					body, M.PreferLinksWithSameLocations, SWT.CHECK);
			config.preferLinksInSameLocation(false);
			check.setSelection(false);
			Controls.onSelect(
					check, $ -> config.preferLinksInSameLocation(check.getSelection()));
			onLinkingChanged.add(linking -> check.setEnabled(linking != ProviderLinking.ONLY_DEFAULTS));
		}
	}
}
