/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.variable.BooleanVariable;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class BooleanVariableAccessExpression extends VariableAccessExpression implements BooleanExpression {
	
	private final BooleanVariable variable;
	
	
	public BooleanVariableAccessExpression(final String name, final BooleanVariable variable) {
		super(name, Type.BOOLEAN);
		this.variable = variable;
	}
	
	public final boolean getValue(final int index) {
		return variable.getValue();
	}
	
}
