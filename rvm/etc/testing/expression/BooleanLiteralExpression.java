/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.parser.LiteralConverter;


/**
 * TODO
 * true or false
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class BooleanLiteralExpression extends LiteralExpression implements BooleanExpression {
	
	private final boolean value;
	
	
	public BooleanLiteralExpression(final boolean value) {
		this.value = value;
	}

	public final Type getType() {
		return Type.BOOLEAN;
	}
	
	public final void unparse(final StringBuffer sb) {
		sb.append(LiteralConverter.unparseBooleanValue(value));
	}
	
	public final boolean getValue(final int index) {
		return value;
	}

}
