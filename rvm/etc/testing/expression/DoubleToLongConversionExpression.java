/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (long)d
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class DoubleToLongConversionExpression extends ConversionExpression implements LongExpression {
	
	private final DoubleExpression a;
	
	
	public DoubleToLongConversionExpression(final DoubleExpression a) {
		super(a, false, Type.LONG);
		this.a = a;
	}
	
	public final DoubleExpression getA() {
		return a;
	}
	
	public final long getValue(final int index) {
		return (long)a.getValue(index);
	}
	
}
