/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Precedence;


/**
 * An Expression that evaluates either the first or the second argument, depending on the condition (condition?a:b).
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public abstract class ConditionalExpression extends AbstractExpression {
	
	private final BooleanExpression condition;
	private final Expression a;
	private final Expression b;
	
	
	public ConditionalExpression(final BooleanExpression condition, final Expression a, final Expression b) {
		this.condition = condition;
		this.a = a;
		this.b = b;
	}
	
	public final int getPrecedence() {
		return Precedence.CONDITIONAL;
	}
	
	public final void unparse(final StringBuffer sb) {
		if (condition.getPrecedence()<=getPrecedence()) {
			sb.append('(');
		}
		condition.unparse(sb);
		if (condition.getPrecedence()<=getPrecedence()) {
			sb.append(')');
		}
		sb.append('?');
		if (a.getPrecedence()<=getPrecedence()) {
			sb.append('(');
		}
		a.unparse(sb);
		if (a.getPrecedence()<=getPrecedence()) {
			sb.append(')');
		}
		sb.append(':');
		if (b.getPrecedence()<=getPrecedence()) {
			sb.append('(');
		}
		b.unparse(sb);
		if (b.getPrecedence()<=getPrecedence()) {
			sb.append(')');
		}
	}

	public BooleanExpression getCondition() {
		return condition;
	}
	
}
