/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.variable.LongVariable;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class LongVariableAccessExpression extends VariableAccessExpression implements LongExpression {
	
	private final LongVariable variable;
	
	
	public LongVariableAccessExpression(final String name, final LongVariable variable) {
		super(name, Type.LONG);
		this.variable = variable;
	}
	
	public final long getValue(final int index) {
		return variable.getValue();
	}
	
}
