/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.parser.LiteralConverter;


/**
 * TODO
 * "Hello", "\"", "", ...
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class StringLiteralExpression extends LiteralExpression implements StringExpression {

	private final String value;
	
	
	public StringLiteralExpression(final String value) {
		this.value = value;
	}

	public final Type getType() {
		return Type.STRING;
	}

	public final void unparse(final StringBuffer sb) {
		sb.append(LiteralConverter.unparseStringValue(value));
	}

	public final String getValue(final int index) {
		return value;
	}
	
}
