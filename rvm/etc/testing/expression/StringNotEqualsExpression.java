/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;


/**
 * TODO
 * s!=s
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class StringNotEqualsExpression extends BinaryExpression implements BooleanExpression {
	
	private final StringExpression a;
	private final StringExpression b;
	
	
	public StringNotEqualsExpression(final StringExpression a, final StringExpression b) {
		super(a, b, Type.BOOLEAN, "!=", Precedence.EQUALITY);
		this.a = a;
		this.b = b;
	}

	public final StringExpression getA() {
		return a;
	}
	
	public final StringExpression getB() {
		return b;
	}
	
	public final boolean getValue(final int index) {
		return !a.getValue(index).equals(b.getValue(index));
	}
	
}
