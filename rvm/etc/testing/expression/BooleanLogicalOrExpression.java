/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;


/**
 * TODO
 * b||b
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class BooleanLogicalOrExpression extends BinaryExpression implements BooleanExpression {

	private final BooleanExpression a;
	private final BooleanExpression b;
	
	
	public BooleanLogicalOrExpression(final BooleanExpression a, final BooleanExpression b) {
		super(a, b, Type.BOOLEAN, "||", Precedence.LOGICAL_OR);
		this.a = a;
		this.b = b;
	}
	
	public final BooleanExpression getA() {
		return a;
	}
	
	public final BooleanExpression getB() {
		return b;
	}
	
	public final boolean getValue(final int index) {
		return a.getValue(index) || b.getValue(index);
	}
	
}
