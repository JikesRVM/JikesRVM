/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * ~l
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class LongBitwiseNotExpression extends UnaryExpression implements LongExpression {

	private final LongExpression a;
	
	
	public LongBitwiseNotExpression(final LongExpression a) {
		super(a, Type.INT, '~');
		this.a = a;
	}

	public final LongExpression getA() {
		return a;
	}
	
	public final long getValue(final int index) {
		return ~a.getValue(index);
	}
	
}
