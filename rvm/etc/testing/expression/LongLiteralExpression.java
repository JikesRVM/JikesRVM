/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.parser.LiteralConverter;


/**
 * TODO
 * 0L, 1200000000L, ...
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class LongLiteralExpression extends LiteralExpression implements LongExpression {

	private final long value;
	
	
	/**
	 * NOTE: should we disallow negative long literals? 
	 * If we only allowed positive values, and were to use negation, we would never be able to enter Long.MIN_VALUE!
	 */
	public LongLiteralExpression(final long value) {
		this.value = value;
	}

	public final Type getType() {
		return Type.LONG;
	}

	public final void unparse(final StringBuffer sb) {
		sb.append(LiteralConverter.unparseLongValue(value));
	}

	public final long getValue(final int index) {
		return value;
	}

}
