/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.function.LongToStringFunction;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class LongToStringFunctionCallExpression extends FunctionCallExpression implements StringExpression {

	private final LongToStringFunction function;
	private final LongExpression parameterExpression;
	
	
	public LongToStringFunctionCallExpression(final String functionName, final LongExpression parameterExpression, final LongToStringFunction function) {
		super(functionName, parameterExpression, Type.STRING);
		this.function = function;
		this.parameterExpression = parameterExpression;
	}
	
	public final String getValue(final int index) {
		function.getParameter().setValue(parameterExpression.getValue(index));
		return function.getValue(index);
	}
	
}
