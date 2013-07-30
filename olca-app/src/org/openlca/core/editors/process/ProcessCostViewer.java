package org.openlca.core.editors.process;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.openlca.app.UI;
import org.openlca.core.model.ProductCostEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The table viewer of the cost entries in the process cost page.
 */
class ProcessCostViewer {

	private Logger log = LoggerFactory.getLogger(getClass());

	private final String COST_CATEGORY = "Cost category";
	private final String AMOUNT = "Amount";
	private final String FIX = "Fixed costs";
	private TableViewer viewer;
	private ProcessEditor editor;

	public ProcessCostViewer(ProcessEditor editor) {
		this.editor = editor;
	}

	public TableViewer getTableViewer() {
		return viewer;
	}

	public void render(FormToolkit toolkit, Composite parent) {
		viewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION
				| SWT.MULTI);
		Table table = viewer.getTable();
		toolkit.adapt(table);
		toolkit.paintBordersFor(parent);
		table.setLinesVisible(true);
		table.setHeaderVisible(true);
		addColumns(table);
		addEditing();
	}

	private void addColumns(Table table) {
		String[] props = { COST_CATEGORY, AMOUNT, FIX };
		viewer.setColumnProperties(props);
		for (String prop : props) {
			TableColumn col = new TableColumn(table, SWT.NONE);
			col.setText(prop);
		}
		UI.bindColumnWidths(table, 0.5, 0.4, 0.1);
	}

	private void addEditing() {
		CellEditor[] editors = new CellEditor[2];
		editors[1] = new TextCellEditor(viewer.getTable());
		viewer.setCellEditors(editors);
		viewer.setCellModifier(new CostCellModifier());
		viewer.setContentProvider(new ArrayContentProvider());
		viewer.setLabelProvider(new LableProvider());
	}

	private class CostCellModifier implements ICellModifier {

		@Override
		public boolean canModify(Object obj, String property) {
			if (!(obj instanceof ProductCostEntry))
				return false;
			if (AMOUNT.equals(property))
				return true;
			return false;
		}

		@Override
		public Object getValue(Object obj, String property) {
			if (!(obj instanceof ProductCostEntry) || !AMOUNT.equals(property))
				return null;
			ProductCostEntry entry = (ProductCostEntry) obj;
			return Double.toString(entry.getAmount());
		}

		@Override
		public void modify(Object element, String property, Object val) {
			if (element instanceof Item)
				element = ((Item) element).getData();
			if (!(element instanceof ProductCostEntry)
					|| !AMOUNT.equals(property) || val == null)
				return;
			ProductCostEntry entry = (ProductCostEntry) element;
			try {
				Double v = Double.parseDouble(val.toString());
				entry.setAmount(v);
				viewer.refresh();
				editor.fireChange();
			} catch (Exception e) {
				log.warn("Number parse error for " + val, e);
			}
		}
	}

	private class LableProvider extends ColumnLabelProvider implements
			ITableLabelProvider {

		@Override
		public Image getColumnImage(Object element, int column) {
			return null;
		}

		@Override
		public String getColumnText(Object element, int column) {
			if (!(element instanceof ProductCostEntry))
				return null;
			ProductCostEntry entry = (ProductCostEntry) element;
			switch (column) {
			case 0:
				return costCategory(entry);
			case 1:
				return Double.toString(entry.getAmount());
			case 2:
				return isFix(entry) ? "Yes" : "No";
			}
			return null;
		}

		private boolean isFix(ProductCostEntry entry) {
			if (entry.getCostCategory() == null)
				return false;
			return entry.getCostCategory().isFix();
		}

		private String costCategory(ProductCostEntry entry) {
			if (entry.getCostCategory() == null)
				return null;
			return entry.getCostCategory().getName();
		}

	}

}
