/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;



/**
 * An Expression that returns an int.
 * 
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public interface IntExpression extends Expression {
	
	public int getValue(int index);

}
