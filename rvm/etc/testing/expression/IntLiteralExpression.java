/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.parser.LiteralConverter;


/**
 * TODO
 * -5, 13900, ...
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class IntLiteralExpression extends LiteralExpression implements IntExpression {

	private final int value;
	
	
	/**
	 * NOTE: should we disallow negative int literals? 
	 * If we only allowed positive values, and were to use negation, we would never be able to enter Integer.MIN_VALUE!
	 */
	public IntLiteralExpression(final int value) {
		this.value = value;
	}

	public final Type getType() {
		return Type.INT;
	}

	public final void unparse(final StringBuffer sb) {
		sb.append(LiteralConverter.unparseIntValue(value));
	}

	public final int getValue(final int index) {
		return value;
	}

}
