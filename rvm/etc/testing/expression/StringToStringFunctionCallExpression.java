/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.function.StringToStringFunction;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class StringToStringFunctionCallExpression extends FunctionCallExpression implements StringExpression {

	private final StringToStringFunction function;
	private final StringExpression parameterExpression;
	
	
	public StringToStringFunctionCallExpression(final String functionName, final StringExpression parameterExpression, final StringToStringFunction function) {
		super(functionName, parameterExpression, Type.STRING);
		this.function = function;
		this.parameterExpression = parameterExpression;
	}
	
	public final String getValue(final int index) {
		function.getParameter().setValue(parameterExpression.getValue(index));
		return function.getValue(index);
	}
	
}
