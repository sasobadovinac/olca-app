package org.openlca.core.editors.process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.openlca.app.UI;
import org.openlca.core.model.Exchange;
import org.openlca.core.model.PedigreeMatrix;
import org.openlca.core.model.PedigreeMatrixRow;

class PedigreeShell extends Shell {

	private final FormToolkit toolkit = new FormToolkit(Display.getDefault());
	private Color[] colors;
	private List<PedigreeCell> dataCells = new ArrayList<>();
	private Exchange exchange;
	private Text baseUncertaintyText;
	private Label valueLabel;

	public PedigreeShell(Shell parent, Exchange exchange) {
		super(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		this.exchange = exchange;
		setLayout(new FillLayout(SWT.HORIZONTAL));
		create();
		setText("Pedigree matrix");
		setSize(830, 600);
		colors = PedigreeShellData.getColors(parent.getDisplay());
		// pack();
		initSelection();
		UI.center(parent, this);
	}

	public Color[] getColors() {
		return colors;
	}

	private void initSelection() {
		Map<PedigreeMatrixRow, Integer> map = null;
		if (exchange != null && exchange.getPedigreeUncertainty() != null)
			map = PedigreeMatrix.fromString(exchange.getPedigreeUncertainty());
		else
			map = PedigreeShellData.defaultSelection();
		for (PedigreeMatrixRow key : map.keySet()) {
			select(key, map.get(key));
		}
		if (exchange != null && exchange.getBaseUncertainty() != null)
			baseUncertaintyText.setText(Double.toString(exchange
					.getBaseUncertainty()));
		calculateSigmaG();
	}

	public Map<PedigreeMatrixRow, Integer> getSelection() {
		Map<PedigreeMatrixRow, Integer> vals = new HashMap<>();
		for (PedigreeCell cell : dataCells) {
			if (cell.isSelected())
				vals.put(cell.getRow(), cell.getScore());
		}
		return vals;
	}

	private void create() {
		ScrolledForm form = toolkit.createScrolledForm(this);
		Composite root = form.getBody();
		UI.gridLayout(root, 1);
		createHeader(root);
		createSeparator(root);
		ceateContent(root);
		createSeparator(root);
		createFooter(root);
	}

	private void ceateContent(Composite root) {
		Composite composite = toolkit.createComposite(root);
		composite.setLayout(new GridLayout(6, false));
		UI.gridData(composite, true, true);
		createContentHeader(composite);
		createContentData(composite);
	}

	private void createContentHeader(Composite composite) {
		toolkit.createLabel(composite, "Indicator score");
		for (int i = 1; i < 6; i++) {
			Label label = toolkit.createLabel(composite, Integer.toString(i));
			label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true,
					false));
		}
	}

	private void createContentData(Composite composite) {
		PedigreeShellData data = new PedigreeShellData();
		for (PedigreeMatrixRow row : PedigreeMatrixRow.values()) {
			String rowText = data.getRowLabel(row);
			Label rowLabel = toolkit.createLabel(composite, rowText, SWT.WRAP);
			GridData gridData = UI.gridData(rowLabel, false, true);
			gridData.widthHint = 120;
			gridData.minimumWidth = 120;
			createRowData(composite, row, data);
		}
	}

	private void createRowData(Composite composite, PedigreeMatrixRow row,
			PedigreeShellData data) {
		for (int i = 1; i < 6; i++) {
			PedigreeCell dataCell = new PedigreeCell(this, row, i);
			dataCell.createComponents(composite, data, toolkit);
			dataCells.add(dataCell);
		}
	}

	private void createHeader(Composite root) {
		Composite header = toolkit.createComposite(root);
		header.setLayout(new GridLayout(1, false));
		UI.gridData(header, true, false);
		toolkit.paintBordersFor(header);
		toolkit.createLabel(header,
				"Click on the matrix cells to select entries");
	}

	private void createSeparator(Composite root) {
		Composite sep = toolkit.createCompositeSeparator(root);
		UI.gridData(sep, true, false).heightHint = 1;
		toolkit.paintBordersFor(sep);
	}

	private void createFooter(Composite root) {
		Composite composite = toolkit.createComposite(root);
		UI.gridLayout(composite, 6);
		UI.gridData(composite, true, false);
		toolkit.paintBordersFor(composite);
		toolkit.createLabel(composite, "Base uncertainty: ");
		baseUncertaintyText = toolkit.createText(composite, "1.0");
		UI.gridData(baseUncertaintyText, false, false).widthHint = 80;
		baseUncertaintyText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				calculateSigmaG();
			}
		});
		toolkit.createLabel(composite, "\u03c3g: ");
		valueLabel = toolkit.createLabel(composite, "");
		UI.gridData(valueLabel, true, false);
		Button okButton = toolkit.createButton(composite, "OK", SWT.NONE);
		UI.gridData(okButton, false, false).widthHint = 60;
		okButton.addSelectionListener(new PedigreeShellFinishHandler(this,
				exchange));
		Button cancelButton = toolkit.createButton(composite, "Cancel",
				SWT.NONE);
		cancelButton.addSelectionListener(new PedigreeShellFinishHandler(this));
		UI.gridData(cancelButton, false, false).widthHint = 60;
	}

	void calculateSigmaG() {
		String baseFactorText = baseUncertaintyText.getText();
		try {
			double baseFactor = Double.parseDouble(baseFactorText);
			double sigma = PedigreeShellData.calculateSigmaG(getSelection(),
					baseFactor);
			valueLabel.setText(Double.toString(sigma));
			baseUncertaintyText.setBackground(getDisplay().getSystemColor(
					SWT.COLOR_WHITE));
			baseUncertaintyText.setToolTipText(null);
		} catch (Exception e) {
			baseUncertaintyText.setBackground(colors[4]);
			baseUncertaintyText.setToolTipText("Invalid value");
		}
	}

	double getBaseValue() {
		String baseFactorText = baseUncertaintyText.getText();
		try {
			return Double.parseDouble(baseFactorText);
		} catch (Exception e) {
			return Double.NaN;
		}
	}

	@Override
	protected void checkSubclass() {
	}

	void select(PedigreeMatrixRow row, int score) {
		for (PedigreeCell cell : dataCells) {
			if (cell.getRow() == row) {
				cell.setSelected(cell.getScore() == score);
				cell.setColor();
			}
		}
	}

}
