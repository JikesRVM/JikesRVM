/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * b?l:l
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class ConditionalLongExpression extends ConditionalExpression implements LongExpression {
	
	private final LongExpression a;
	private final LongExpression b;
	
	
	public ConditionalLongExpression(final BooleanExpression condition, final LongExpression a, final LongExpression b) {
		super(condition, a, b);
		this.a = a;
		this.b = b;
	}

	public final Type getType() {
		return Type.LONG;
	}

	public final LongExpression getA() {
		return a;
	}
	
	public final LongExpression getB() {
		return b;
	}
	
	public final long getValue(final int index) {
		return getCondition().getValue(index)?a.getValue(index):b.getValue(index);
	}
	
}
