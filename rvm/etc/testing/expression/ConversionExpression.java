/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;


/**
 * A ConversionExpression converts the value of an Expression into a different type.
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public abstract class ConversionExpression extends AbstractExpression {
	
	private final Expression a;
	private final boolean implicit;
	private final Type type;
	
	
	public ConversionExpression(final Expression a, final boolean implicit, final Type type) {
		this.a = a;
		this.implicit = implicit;
		this.type = type;
	}

	public final Type getType() {
		return type;
	}
		
	public final int getPrecedence() {
		return implicit?a.getPrecedence():Precedence.CAST;
	}

	public final void unparse(final StringBuffer sb) {
		if (implicit) {
			a.unparse(sb);
		} else {
			sb.append('(');
			sb.append(type.getName());
			sb.append(')');
			if (a.getPrecedence()<=getPrecedence()) {
				sb.append('(');
			}
			a.unparse(sb);
			if (a.getPrecedence()<=getPrecedence()) {
				sb.append(')');
			}
		}
	}
	
}
