/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;


/**
 * TODO
 * d<=d
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class DoubleLessEqualExpression extends BinaryExpression implements BooleanExpression {

	private final DoubleExpression a;
	private final DoubleExpression b;
	
	
	public DoubleLessEqualExpression(final DoubleExpression a, final DoubleExpression b) {
		super(a, b, Type.BOOLEAN, "<=", Precedence.RELATIONAL);
		this.a = a;
		this.b = b;
	}
	
	public final DoubleExpression getA() {
		return a;
	}
	
	public final DoubleExpression getB() {
		return b;
	}
	
	public final boolean getValue(final int index) {
		return a.getValue(index) <= b.getValue(index);
	}
	
}
