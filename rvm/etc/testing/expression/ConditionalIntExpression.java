/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * b?i:i
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class ConditionalIntExpression extends ConditionalExpression implements IntExpression {
	
	private final IntExpression a;
	private final IntExpression b;
	
	
	public ConditionalIntExpression(final BooleanExpression condition, final IntExpression a, final IntExpression b) {
		super(condition, a, b);
		this.a = a;
		this.b = b;
	}

	public final Type getType() {
		return Type.INT;
	}

	public final IntExpression getA() {
		return a;
	}
	
	public final IntExpression getB() {
		return b;
	}
	
	public final int getValue(final int index) {
		return getCondition().getValue(index)?a.getValue(index):b.getValue(index);
	}
	
}
