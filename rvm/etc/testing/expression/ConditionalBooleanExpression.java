/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * b?b:b
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class ConditionalBooleanExpression extends ConditionalExpression implements BooleanExpression {
	
	private final BooleanExpression a;
	private final BooleanExpression b;
	
	
	public ConditionalBooleanExpression(final BooleanExpression condition, final BooleanExpression a, final BooleanExpression b) {
		super(condition, a, b);
		this.a = a;
		this.b = b;
	}

	public final Type getType() {
		return Type.BOOLEAN;
	}

	public final BooleanExpression getA() {
		return a;
	}
	
	public final BooleanExpression getB() {
		return b;
	}
	
	public final boolean getValue(final int index) {
		return getCondition().getValue(index)?a.getValue(index):b.getValue(index);
	}
	
}
