/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (int)d
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class DoubleToIntConversionExpression extends ConversionExpression implements IntExpression {
	
	private final DoubleExpression a;
	
	
	public DoubleToIntConversionExpression(final DoubleExpression a) {
		super(a, false, Type.INT);
		this.a = a;
	}
	
	public final DoubleExpression getA() {
		return a;
	}
	
	public final int getValue(final int index) {
		return (int)a.getValue(index);
	}
	
}
