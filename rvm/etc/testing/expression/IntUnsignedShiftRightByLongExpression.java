/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;


/**
 * TODO
 * i>>>l
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class IntUnsignedShiftRightByLongExpression extends BinaryExpression implements IntExpression {

	private final IntExpression a;
	private final LongExpression b;
	
	
	public IntUnsignedShiftRightByLongExpression(final IntExpression a, final LongExpression b) {
		super(a, b, Type.INT, ">>>", Precedence.SHIFT);
		this.a = a;
		this.b = b;
	}
	
	public final IntExpression getA() {
		return a;
	}
	
	public final LongExpression getB() {
		return b;
	}
	
	public final int getValue(final int index) {
		return a.getValue(index) >>> b.getValue(index);
	}
	
}
