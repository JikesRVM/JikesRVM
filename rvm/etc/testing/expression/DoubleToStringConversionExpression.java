/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * implicit conversion in s+d
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class DoubleToStringConversionExpression extends ConversionExpression implements StringExpression {
	
	private final DoubleExpression a;
	
	
	public DoubleToStringConversionExpression(final DoubleExpression a) {
		super(a, true, Type.STRING);
		this.a = a;
	}
	
	public final DoubleExpression getA() {
		return a;
	}
	
	public final String getValue(final int index) {
		return Double.toString(a.getValue(index));
	}
	
}
