/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.trace.field.variable.ByteFieldVariable;


/**
 * TODO
 * #fieldVariableName
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class ByteFieldVariableAccessExpression extends FieldVariableAccessExpression implements ByteExpression {
	
	private final ByteFieldVariable fieldVariable;
	
	
	public ByteFieldVariableAccessExpression(final ByteFieldVariable fieldVariable) {
		super(fieldVariable);
		this.fieldVariable = fieldVariable;
	}
	
	public final byte getValue(final int index) {
		return fieldVariable.getValue();
	}
	
}
