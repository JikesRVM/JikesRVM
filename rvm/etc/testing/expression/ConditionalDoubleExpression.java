/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * b?d:d
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class ConditionalDoubleExpression extends ConditionalExpression implements DoubleExpression {
	
	private final DoubleExpression a;
	private final DoubleExpression b;
	
	
	public ConditionalDoubleExpression(final BooleanExpression condition, final DoubleExpression a, final DoubleExpression b) {
		super(condition, a, b);
		this.a = a;
		this.b = b;
	}

	public final Type getType() {
		return Type.DOUBLE;
	}
	
	public final DoubleExpression getA() {
		return a;
	}
	
	public final DoubleExpression getB() {
		return b;
	}
	
	public final double getValue(final int index) {
		return getCondition().getValue(index)?a.getValue(index):b.getValue(index);
	}
	
}
