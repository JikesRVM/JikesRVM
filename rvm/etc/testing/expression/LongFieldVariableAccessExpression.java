/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.trace.field.variable.LongFieldVariable;


/**
 * TODO
 * #fieldVariableName
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class LongFieldVariableAccessExpression extends FieldVariableAccessExpression implements LongExpression {
	
	private final LongFieldVariable fieldVariable;
	
	
	public LongFieldVariableAccessExpression(final LongFieldVariable fieldVariable) {
		super(fieldVariable);
		this.fieldVariable = fieldVariable;
	}
	
	public final long getValue(final int index) {
		return fieldVariable.getValue();
	}
	
}
