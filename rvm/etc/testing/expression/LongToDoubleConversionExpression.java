/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (double)l
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class LongToDoubleConversionExpression extends ConversionExpression implements DoubleExpression {
	
	private final LongExpression a;
	
	
	public LongToDoubleConversionExpression(final LongExpression a, final boolean implicit) {
		super(a, implicit, Type.DOUBLE);
		this.a = a;
	}

	public final LongExpression getA() {
		return a;
	}

	public final double getValue(final int index) {
		return (double)a.getValue(index);
	}
	
}
