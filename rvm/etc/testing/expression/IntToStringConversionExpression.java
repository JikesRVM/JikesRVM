/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * implicit conversion in s+i
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class IntToStringConversionExpression extends ConversionExpression implements StringExpression {
	
	private final IntExpression a;
	
	
	public IntToStringConversionExpression(final IntExpression a) {
		super(a, true, Type.STRING);
		this.a = a;
	}

	public final IntExpression getA() {
		return a;
	}

	public final String getValue(final int index) {
		return Integer.toString(a.getValue(index));
	}
	
}
