/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (long)l
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class LongToLongConversionExpression extends ConversionExpression implements LongExpression {
	
	private final LongExpression a;
	
	
	public LongToLongConversionExpression(final LongExpression a) {
		super(a, false, Type.LONG);
		this.a = a;
	}

	public final LongExpression getA() {
		return a;
	}

	public final long getValue(final int index) {
		return a.getValue(index);
	}
	
}
