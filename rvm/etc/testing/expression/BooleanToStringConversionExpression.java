/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * implicit conversion in s+b
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class BooleanToStringConversionExpression extends ConversionExpression implements StringExpression {
	
	private final BooleanExpression a;
	
	
	public BooleanToStringConversionExpression(final BooleanExpression a) {
		super(a, true, Type.STRING);
		this.a = a;
	}
	
	public final BooleanExpression getA() {
		return a;
	}
	
	public final String getValue(final int index) {
		return Boolean.toString(a.getValue(index));
	}
	
}
