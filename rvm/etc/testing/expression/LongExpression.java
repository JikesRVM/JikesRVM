/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;



/**
 * An Expression that returns a long.
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public interface LongExpression extends Expression {
	
	public long getValue(int index);

}
