/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (int)i
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class IntToIntConversionExpression extends ConversionExpression implements IntExpression {
	
	private final IntExpression a;
	
	
	public IntToIntConversionExpression(final IntExpression a) {
		super(a, false, Type.INT);
		this.a = a;
	}

	public final IntExpression getA() {
		return a;
	}
	
	public final int getValue(final int index) {
		return a.getValue(index);
	}
	
}
