/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;
import com.ibm.research.pe.model.trace.Trace;
import com.ibm.research.pe.model.trace.TraceRecordList;
import com.ibm.research.pe.model.trace.field.variable.FieldVariable;


/**
 * A FieldVariableAccessExpression accesses a field variable (a field outside a trace record).
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public abstract class FieldVariableAccessExpression extends AbstractExpression {
	
	private final FieldVariable fieldVariable;
	
	
	public FieldVariableAccessExpression(final FieldVariable fieldVariable) {
		this.fieldVariable = fieldVariable;
	}
	
	public void bind(final Trace trace, final TraceRecordList traceRecordList) {
	}
	
	public final Type getType() {
		return fieldVariable.getType().getExpressionType();
	}
	
	public final int getPrecedence() {
		return Precedence.ATOM;
	}

	public final void unparse(final StringBuffer sb) {
		sb.append(fieldVariable.getName());
	}

	public final String getFieldName() {
		return fieldVariable.getName();
	}
	
}
