/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.function.DoubleToDoubleFunction;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class DoubleToDoubleFunctionCallExpression extends FunctionCallExpression implements DoubleExpression {

	private final DoubleToDoubleFunction function;
	private final DoubleExpression parameterExpression;
	
	
	public DoubleToDoubleFunctionCallExpression(final String functionName, final DoubleExpression parameterExpression, final DoubleToDoubleFunction function) {
		super(functionName, parameterExpression, Type.DOUBLE);
		this.function = function;
		this.parameterExpression = parameterExpression;
	}
	
	public final double getValue(final int index) {
		function.getParameter().setValue(parameterExpression.getValue(index));
		return function.getValue(index);
	}
	
}
