/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * b?s:s
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class ConditionalStringExpression extends ConditionalExpression implements StringExpression {
	
	private final StringExpression a;
	private final StringExpression b;
	
	
	public ConditionalStringExpression(final BooleanExpression condition, final StringExpression a, final StringExpression b) {
		super(condition, a, b);
		this.a = a;
		this.b = b;
	}

	public final Type getType() {
		return Type.STRING;
	}

	public final StringExpression getA() {
		return a;
	}
	
	public final StringExpression getB() {
		return b;
	}
	
	public final String getValue(final int index) {
		return getCondition().getValue(index)?a.getValue(index):b.getValue(index);
	}
	
}
