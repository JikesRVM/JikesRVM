/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;


/**
 * TODO
 * l>>i
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class LongShiftRightByIntExpression extends BinaryExpression implements LongExpression {

	private final LongExpression a;
	private final IntExpression b;
	
	
	public LongShiftRightByIntExpression(final LongExpression a, final IntExpression b) {
		super(a, b, Type.LONG, ">>", Precedence.SHIFT);
		this.a = a;
		this.b = b;
	}

	public final LongExpression getA() {
		return a;
	}
	
	public final IntExpression getB() {
		return b;
	}
	
	public final long getValue(final int index) {
		return a.getValue(index) >> b.getValue(index);
	}
	
}
