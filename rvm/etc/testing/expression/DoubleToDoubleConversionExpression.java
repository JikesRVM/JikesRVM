/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (double)d
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class DoubleToDoubleConversionExpression extends ConversionExpression implements DoubleExpression {
	
	private final DoubleExpression a;
	
	
	public DoubleToDoubleConversionExpression(final DoubleExpression a) {
		super(a, false, Type.DOUBLE);
		this.a = a;
	}
	
	public final DoubleExpression getA() {
		return a;
	}
	
	public final double getValue(final int index) {
		return a.getValue(index);
	}
	
}
