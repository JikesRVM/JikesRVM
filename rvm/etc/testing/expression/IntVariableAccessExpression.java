/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.variable.IntVariable;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class IntVariableAccessExpression extends VariableAccessExpression implements IntExpression {
	
	private final IntVariable variable;
	
	
	public IntVariableAccessExpression(final String name, final IntVariable variable) {
		super(name, Type.INT);
		this.variable = variable;
	}
	
	public final int getValue(final int index) {
		return variable.getValue();
	}
	
}
