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

/**
 * A scalar type (e.g. the type of a field of an event).
 */
public final class ScalarType {

    /**
     * The scalar type representing a Java integer.
     */
    public static final ScalarType INT = new ScalarType("int", "Java int");

    /**
     * The scalar type representing a Java long.
     */
    public static final ScalarType LONG = new ScalarType("long", "Java long");

    /**
     * The scalar type representing a Java double.
     */
    public static final ScalarType DOUBLE = new ScalarType("double", "Java double");

    /**
     * The scalar type representing a Java String.
     */
    public static final ScalarType STRING = new ScalarType("string", "Java String");

    private final String name;
    private final String description;

    private ScalarType(final String name, final String description) {
	this.name = name;
	this.description = description;
    }

    /**
     * Return the name of this type.
     *
     * @return The name of the type.
     */
    public final String getName() {
	return name;
    }

    /**
     * Return the description of this type.
     *
     * @return The description of the type.
     */
    public final String getDescription() {
	return description;
    }

    /**
     * Return a string representing this scalar type.
     *
     * @return The string representing this scalar type.
     */
    @Override
    public final String toString() {
	return name;
    }
}
