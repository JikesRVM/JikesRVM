/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;


/**
 * Currently only supports functions with zero or one parameters.
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public abstract class FunctionCallExpression extends AbstractExpression {
	
	private final String functionName;
	private final Expression parameterExpression;
	private final Type type;
	
	
	public FunctionCallExpression(final String functionName, final Expression parameterExpression, final Type type) {
		this.functionName = functionName;
		this.parameterExpression = parameterExpression;
		this.type = type;
	}
	
	public final Type getType() {
		return type;
	}
	
	public final int getPrecedence() {
		return Precedence.POSTFIX;
	}
	
	public final void unparse(final StringBuffer sb) {
		sb.append(functionName);
		sb.append("(");
		if (parameterExpression!=null) {
			parameterExpression.unparse(sb);
		}
		sb.append(")");
	}

	public final String getFunctionName() {
		return functionName;
	}

	public final Expression getParameterExpression() {
		return parameterExpression;
	}
	
}
