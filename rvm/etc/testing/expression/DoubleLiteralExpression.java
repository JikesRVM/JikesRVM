/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.parser.LiteralConverter;


/**
 * TODO
 * 1.0, 0.09e15, ...
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class DoubleLiteralExpression extends LiteralExpression implements DoubleExpression {

	private final double value;
	
	
	public DoubleLiteralExpression(final double value) {
		this.value = value;
	}

	public final Type getType() {
		return Type.DOUBLE;
	}
	
	public final void unparse(final StringBuffer sb) {
		sb.append(LiteralConverter.unparseDoubleValue(value));
	}

	public final double getValue(final int index) {
		return value;
	}
	
}
