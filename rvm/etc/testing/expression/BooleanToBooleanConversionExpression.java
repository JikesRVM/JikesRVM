/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (boolean)b
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class BooleanToBooleanConversionExpression extends ConversionExpression implements BooleanExpression {
	
	private final BooleanExpression a;
	
	
	public BooleanToBooleanConversionExpression(final BooleanExpression a) {
		super(a, false, Type.BOOLEAN);
		this.a = a;
	}
	
	public final BooleanExpression getA() {
		return a;
	}
	
	public final boolean getValue(final int index) {
		return a.getValue(index);
	}
	
}
