/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * -d
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class DoubleNegationExpression extends UnaryExpression implements DoubleExpression {

	private final DoubleExpression a;
	
	
	public DoubleNegationExpression(final DoubleExpression a) {
		super(a, Type.DOUBLE, '-');
		this.a = a;
	}

	public final DoubleExpression getA() {
		return a;
	}
	
	public final double getValue(final int index) {
		return -a.getValue(index);
	}
	
}
