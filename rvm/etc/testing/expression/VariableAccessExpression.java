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
public abstract class VariableAccessExpression extends AbstractExpression {
	
	final String name;
	final Type type;
	
	
	public VariableAccessExpression(final String name, final Type type) {
		this.name = name;
		this.type = type;
	}
	
	public final Type getType() {
		return type;
	}
	
	public final int getPrecedence() {
		return Precedence.ATOM;
	}
	
	public final String getName() {
		return name;
	}
	
	public final void unparse(final StringBuffer sb) {
		sb.append(name);
	}
	
}
