/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.function.VoidToBooleanFunction;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class VoidToBooleanFunctionCallExpression extends FunctionCallExpression implements BooleanExpression {

	private final VoidToBooleanFunction function;
	
	
	public VoidToBooleanFunctionCallExpression(final String functionName, final VoidToBooleanFunction function) {
		super(functionName, null, Type.BOOLEAN);
		this.function = function;
	}
	
	public final boolean getValue(final int index) {
		return function.getValue(index);
	}
	
}
