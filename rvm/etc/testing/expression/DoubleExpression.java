/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;


/**
 * An Expression that returns a double.
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public interface DoubleExpression extends Expression {
	
	public double getValue(int index);

}
