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

import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;

/**
 * An EventType describes the types and attributes of the data values associated
 * with a particular event index.
 */
@Uninterruptible
public final class EventType {

    private final int index;
    private final String name;
    private final String description;
    private final EventAttribute[] attributes;
    private final int numberOfInts;
    private final int numberOfLongs;
    private final int numberOfDoubles;
    private final int numberOfStrings;

    private static int currentIndex = 0;

    /**
     * Does this event type accept the supplied number of attributes of each
     * type.
     *
     * @param numInt
     *                The number of int attributes.
     * @param numLong
     *                The number of long attributes.
     * @param numDouble
     *                The number of double attributes.
     * @param numString
     *                The number of String attributes.
     * @return True if the event will accept attributes of the specified types.
     */
    public boolean admits(int numInt, int numLong, int numDouble, int numString) {
	return (numInt == numberOfInts) && (numLong == numberOfLongs)
		&& (numDouble == numberOfDoubles)
		&& (numString == numberOfStrings);
    }

    /**
     * Create a new event type with no attributes.
     *
     * @param name
     *                The name of the event type.
     * @param description
     *                A description of the event type.
     */
    public EventType(String name, String description) {
	this(name, description, new EventAttribute[] {});
    }

    /**
     * Create a new event type with a single attribute.
     *
     * @param name
     *                The name of the event type.
     * @param description
     *                A description of the event type.
     * @param attribute
     *                The event attribute for this event type.
     */
    public EventType(String name, String description, EventAttribute attribute) {
	this(name, description, new EventAttribute[] { attribute });
    }

    @Interruptible
    private static synchronized int getNextIndex() {
	return currentIndex++;
    }

    /**
     * Create a new event type with the supplied attributes.
     *
     * @param name
     *                The name of the event type.
     * @param description
     *                A description of the event type.
     * @param attributes
     *                The event attributes for this event type.
     */
    public EventType(String name, String description,
	    EventAttribute[] attributes) {
	this.index = getNextIndex();
	this.name = name;
	this.description = description;
	this.attributes = attributes;
	int ic = 0;
	int lc = 0;
	int dc = 0;
	int sc = 0;
	for (int i = 0; i < attributes.length; i++) {
	    ScalarType at = attributes[i].getType();
	    if (at.equals(ScalarType.INT)) {
		ic++;
		checkOrder(lc + dc + sc);
	    } else if (at.equals(ScalarType.LONG)) {
		lc++;
		checkOrder(dc + sc);
	    } else if (at.equals(ScalarType.DOUBLE)) {
		dc++;
		checkOrder(sc);
	    } else if (at.equals(ScalarType.STRING)) {
		sc++;
	    } else {
		throw new IllegalArgumentException("EventType constructor: Unsupported event attribute type");
	    }
	}
	numberOfInts = ic;
	numberOfLongs = lc;
	numberOfDoubles = dc;
	numberOfStrings = sc;
    }

    /**
     * Check that attributes are declared in the proper order
     *
     * @param others
     *                the sum of the attribute declarations that should come
     *                later but have already occurred
     */
    @Interruptible
    private void checkOrder(int others) {
	if (others > 0) {
	    throw new IllegalArgumentException("EventType constructor: attributes not declared in the required order int/long/double/String");
	}
    }

    public final int getIndex() {
	return index;
    }

    /**
     * Return the name of this event type.
     *
     * @return The name of this event type.
     */
    public final String getName() {
	return name;
    }

    /**
     * Return the description of this event type.
     *
     * @return The description of this event type.
     */
    public final String getDescription() {
	return description;
    }

    /**
     * Return the number of attributes this event type accepts.
     *
     * @return The number of attributes.
     */
    public final int getNumberOfAttributes() {
	return attributes.length;
    }

    /**
     * Return the number of int attributes this event type accepts.
     *
     * @return The number of int attributes.
     */
    public final int getNumberOfInts() {
	return numberOfInts;
    }

    /**
     * Return the number of long attributes this event type accepts.
     *
     * @return The number of long attributes.
     */
    public final int getNumberOfLongs() {
	return numberOfLongs;
    }

    /**
     * Return the number of double attributes this event type accepts.
     *
     * @return The number of double attributes.
     */
    public final int getNumberOfDoubles() {
	return numberOfDoubles;
    }

    /**
     * Return the number of string attributes this event type accepts.
     *
     * @return The number of string attributes.
     */
    public final int getNumberOfStrings() {
	return numberOfStrings;
    }

    /**
     * Return the ith attribute of this event type.
     *
     * @param i The index of the attribute to return.
     * @return The attribute.
     */
    public final EventAttribute getAttribute(final int i) {
	return attributes[i];
    }

}
