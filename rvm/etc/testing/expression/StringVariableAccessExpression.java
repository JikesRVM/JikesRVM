/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.variable.StringVariable;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class StringVariableAccessExpression extends VariableAccessExpression implements StringExpression {
	
	private final StringVariable variable;
	
	
	public StringVariableAccessExpression(final String name, final StringVariable variable) {
		super(name, Type.STRING);
		this.variable = variable;
	}
	
	public final String getValue(final int index) {
		return variable.getValue();
	}
	
}
