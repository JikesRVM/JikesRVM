/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (int)l
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class LongToIntConversionExpression extends ConversionExpression implements IntExpression {
	
	private final LongExpression a;
	
	
	public LongToIntConversionExpression(final LongExpression a) {
		super(a, false, Type.INT);
		this.a = a;
	}

	public final LongExpression getA() {
		return a;
	}

	public final int getValue(final int index) {
		return (int)a.getValue(index);
	}
	
}
