/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;



/**
 * An Expression that returns a String.
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public interface StringExpression extends Expression {
	
	public String getValue(int index);

}
