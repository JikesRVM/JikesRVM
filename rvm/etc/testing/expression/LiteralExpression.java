/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Precedence;


/**
 * A LiteralExpression represents a constant.
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public abstract class LiteralExpression extends AbstractExpression {
	
	public final int getPrecedence() {
		return Precedence.ATOM;
	}

}
