/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.trace.field.variable.ShortFieldVariable;


/**
 * TODO
 * #fieldVariableName
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class ShortFieldVariableAccessExpression extends FieldVariableAccessExpression implements ShortExpression {
	
	private final ShortFieldVariable fieldVariable;
	
	
	public ShortFieldVariableAccessExpression(final ShortFieldVariable fieldVariable) {
		super(fieldVariable);
		this.fieldVariable = fieldVariable;
	}
	
	public final short getValue(final int index) {
		return fieldVariable.getValue();
	}
	
}
