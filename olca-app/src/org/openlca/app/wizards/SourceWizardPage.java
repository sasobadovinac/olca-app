/*******************************************************************************
 * Copyright (c) 2007 - 2010 GreenDeltaTC. All rights reserved. This program and
 * the accompanying materials are made available under the terms of the Mozilla
 * Public License v1.1 which accompanies this distribution, and is available at
 * http://www.openlca.org/uploads/media/MPL-1.1.html
 * 
 * Contributors: GreenDeltaTC - initial API and implementation
 * www.greendeltatc.com tel.: +49 30 4849 6030 mail: gdtc@greendeltatc.com
 ******************************************************************************/

package org.openlca.app.wizards;

import java.util.UUID;

import org.eclipse.swt.widgets.Composite;
import org.openlca.app.Messages;
import org.openlca.app.resources.ImageType;
import org.openlca.core.model.Source;

public class SourceWizardPage extends AbstractWizardPage<Source> {

	public SourceWizardPage() {
		super("SourceWizardPage");
		setTitle(Messages.Sources_WizardTitle);
		setMessage(Messages.Sources_WizardMessage);
		setImageDescriptor(ImageType.NEW_WIZ_SOURCE.getDescriptor());
		setPageComplete(false);
	}

	@Override
	protected void createContents(final Composite container) {
	}

	@Override
	public Source createModel() {
		Source source = new Source();
		source.setRefId(UUID.randomUUID().toString());
		source.setName(getModelName());
		source.setDescription(getModelDescription());
		return source;
	}
}
