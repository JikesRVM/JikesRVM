/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (long)i
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class IntToLongConversionExpression extends ConversionExpression implements LongExpression {
	
	private final IntExpression a;
	
	
	public IntToLongConversionExpression(final IntExpression a, final boolean implicit) {
		super(a, implicit, Type.LONG);
		this.a = a;
	}

	public final IntExpression getA() {
		return a;
	}

	public final long getValue(final int index) {
		return (long)a.getValue(index);
	}
	
}
