/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.trace.field.variable.IntFieldVariable;


/**
 * TODO
 * #fieldVariableName
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class IntFieldVariableAccessExpression extends FieldVariableAccessExpression implements IntExpression {
	
	private final IntFieldVariable fieldVariable;
	
	
	public IntFieldVariableAccessExpression(final IntFieldVariable fieldVariable) {
		super(fieldVariable);
		this.fieldVariable = fieldVariable;
	}
	
	public final int getValue(final int index) {
		return fieldVariable.getValue();
	}
	
}
