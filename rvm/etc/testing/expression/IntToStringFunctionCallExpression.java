/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.function.IntToStringFunction;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class IntToStringFunctionCallExpression extends FunctionCallExpression implements StringExpression {

	private final IntToStringFunction function;
	private final IntExpression parameterExpression;
	
	
	public IntToStringFunctionCallExpression(final String functionName, final IntExpression parameterExpression, final IntToStringFunction function) {
		super(functionName, parameterExpression, Type.STRING);
		this.function = function;
		this.parameterExpression = parameterExpression;
	}
	
	public final String getValue(final int index) {
		function.getParameter().setValue(parameterExpression.getValue(index));
		return function.getValue(index);
	}
	
}
