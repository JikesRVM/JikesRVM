/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (double)i
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class IntToDoubleConversionExpression extends ConversionExpression implements DoubleExpression {
	
	private final IntExpression a;
	
	
	public IntToDoubleConversionExpression(final IntExpression a, final boolean implicit) {
		super(a, implicit, Type.DOUBLE);
		this.a = a;
	}

	public final IntExpression getA() {
		return a;
	}
	
	public final double getValue(final int index) {
		return (double)a.getValue(index);
	}
	
}
