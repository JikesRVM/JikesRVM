/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * implicit conversion in s+l
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class LongToStringConversionExpression extends ConversionExpression implements StringExpression {
	
	private final LongExpression a;
	
	
	public LongToStringConversionExpression(final LongExpression a) {
		super(a, true, Type.STRING);
		this.a = a;
	}

	public final LongExpression getA() {
		return a;
	}

	public final String getValue(final int index) {
		return Long.toString(a.getValue(index));
	}
	
}
