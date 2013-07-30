package org.openlca.core.application.views;

import java.util.Objects;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPersistableElement;
import org.openlca.app.Images;
import org.openlca.core.model.descriptors.BaseDescriptor;

/**
 * The basic editor input which contains a model descriptor.
 */
public final class ModelEditorInput implements IEditorInput {

	private BaseDescriptor descriptor;

	public ModelEditorInput(BaseDescriptor descriptor) {
		this.descriptor = descriptor;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof ModelEditorInput) {
			ModelEditorInput other = (ModelEditorInput) obj;
			return Objects.equals(this.descriptor, other.descriptor);
		}
		return false;
	}

	@Override
	public boolean exists() {
		return descriptor != null;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public Object getAdapter(final Class adapter) {
		return null;
	}

	public BaseDescriptor getDescriptor() {
		return descriptor;
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		if (descriptor == null || descriptor.getModelType() == null)
			return null;
		return Images.getIconDescriptor(descriptor.getModelType());
	}

	@Override
	public String getName() {
		if (descriptor == null)
			return "no content";
		return descriptor.getDisplayName();
	}

	@Override
	public IPersistableElement getPersistable() {
		return null;
	}

	@Override
	public String getToolTipText() {
		if (descriptor == null)
			return "no content";
		return descriptor.getName();
	}

}
