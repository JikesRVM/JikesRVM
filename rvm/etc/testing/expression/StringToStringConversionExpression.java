/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (String)s
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class StringToStringConversionExpression extends ConversionExpression implements StringExpression {
	
	private final StringExpression a;
	
	
	public StringToStringConversionExpression(final StringExpression a) {
		super(a, false, Type.STRING);
		this.a = a;
	}
	
	public final StringExpression getA() {
		return a;
	}
	
	public final String getValue(final int index) {
		return a.getValue(index);
	}
	
}
