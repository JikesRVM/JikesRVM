/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * !b
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class BooleanLogicalNotExpression extends UnaryExpression implements BooleanExpression {

	private final BooleanExpression a;
	
	
	public BooleanLogicalNotExpression(final BooleanExpression a) {
		super(a, Type.BOOLEAN, '!');
		this.a = a;
	}

	public final BooleanExpression getA() {
		return a;
	}
	
	public final boolean getValue(final int index) {
		return !a.getValue(index);
	}
	
}
