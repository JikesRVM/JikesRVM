/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public abstract class UnaryExpression extends AbstractExpression {
	
	private final Expression a;
	private final Type type;
	private final char operator;
	
	
	public UnaryExpression(final Expression a, final Type type, final char operator) {
		this.a = a;
		this.type = type;
		this.operator = operator;
	}
	
	public final Type getType() {
		return type;
	}
	
	public final int getPrecedence() {
		return Precedence.UNARY;
	}
	
	public final void unparse(final StringBuffer sb) {
		sb.append(operator);
		if (a.getPrecedence()<=getPrecedence()) {
			sb.append('(');
		}
		a.unparse(sb);
		if (a.getPrecedence()<=getPrecedence()) {
			sb.append(')');
		}
	}

}
