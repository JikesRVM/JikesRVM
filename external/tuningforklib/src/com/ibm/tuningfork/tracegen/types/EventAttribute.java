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

import org.vmmagic.pragma.Uninterruptible;


/**
 * An attribute of an EventType.
 */
@Uninterruptible
public final class EventAttribute {

    private final String name;
    private final String description;
    private final ScalarType type;

    /**
     * Create an EventAttribute with the specified name, description and type.
     *
     * @param name
     *                The name of the attribute.
     * @param description
     *                A description of the attribute.
     * @param type
     *                The type of the attribute.
     */
    public EventAttribute(final String name, final String description,
	    final ScalarType type) {
	this.name = name;
	this.description = description;
	this.type = type;
    }

    /**
     * Return the name of the attribute.
     *
     * @return The name of the attribute.
     */
    public final String getName() {
	return name;
    }

    /**
     * Return the description of the attribute.
     *
     * @return The description of the attribute.
     */
    public final String getDescription() {
	return description;
    }

    /**
     * Return the type of the attribute.
     *
     * @return The type of the attribute.
     */
    public final ScalarType getType() {
	return type;
    }
}
