/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;


/**
 * A LogicalFieldAccessExpression accesses a logical (computed) field in a trace record.
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public abstract class LogicalFieldAccessExpression extends AbstractExpression {
	
	private final String fieldName;
	private final Type type;
	
	
	public LogicalFieldAccessExpression(final String fieldName, final Type type) {
		this.fieldName = fieldName;
		this.type = type;
	}
	
	public final Type getType() {
		return type;
	}
	
	public final int getPrecedence() {
		return Precedence.ATOM;
	}

	public final void unparse(final StringBuffer sb) {
		sb.append(fieldName);
	}

	public final String getFieldName() {
		return fieldName;
	}
	
}
