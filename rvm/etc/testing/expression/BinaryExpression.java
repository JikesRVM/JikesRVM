/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public abstract class BinaryExpression extends AbstractExpression {
	
	private final Expression a;
	private final Expression b;
	private final Type type;
	private final String operator;
	private final int precedence;
	
	
	public BinaryExpression(final Expression a, final Expression b, final Type type, final String operator, final int precedence) {
		this.a = a;
		this.b = b;
		this.type = type;
		this.operator = operator;
		this.precedence = precedence;
	}
	
	public final Type getType() {
		return type;
	}
	
	public final int getPrecedence() {
		return precedence;
	}
	
	public final void unparse(final StringBuffer sb) {
		if (a.getPrecedence()<=getPrecedence()) {
			sb.append('(');
		}
		a.unparse(sb);
		if (a.getPrecedence()<=getPrecedence()) {
			sb.append(')');
		}
		sb.append(operator);
		if (b.getPrecedence()<=getPrecedence()) {
			sb.append('(');
		}
		b.unparse(sb);
		if (b.getPrecedence()<=getPrecedence()) {
			sb.append(')');
		}
	}
	
}
