/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.variable.DoubleVariable;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class DoubleVariableAccessExpression extends VariableAccessExpression implements DoubleExpression {
	
	private final DoubleVariable variable;
	
	
	public DoubleVariableAccessExpression(final String name, final DoubleVariable variable) {
		super(name, Type.DOUBLE);
		this.variable = variable;
	}
	
	public final double getValue(final int index) {
		return variable.getValue();
	}
	
}
