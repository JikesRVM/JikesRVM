/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;


/**
 * TODO
 * l-l
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class LongSubtractionExpression extends BinaryExpression implements LongExpression {

	private final LongExpression a;
	private final LongExpression b;
	
	
	public LongSubtractionExpression(final LongExpression a, final LongExpression b) {
		super(a, b, Type.LONG, "-", Precedence.ADDITIVE);
		this.a = a;
		this.b = b;
	}

	public final LongExpression getA() {
		return a;
	}
	
	public final long getValue(final int index) {
		return a.getValue(index) - b.getValue(index);
	}
	
}
