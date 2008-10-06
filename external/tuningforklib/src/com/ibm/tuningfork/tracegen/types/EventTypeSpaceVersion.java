/*
 * This file is part of the Tuning Fork Visualization Platform
 *  (http://sourceforge.net/projects/tuningforkvp)
 *
 * Copyright (c) 2005 - 2008 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */

package com.ibm.tuningfork.tracegen.types;

public class EventTypeSpaceVersion {

    public final String name;
    public final int version;

    public EventTypeSpaceVersion(String n, int v) {
	name = n;
	version = v;
    }

    public String toString() {
	return name + "(v " + version + ")";
    }
}
