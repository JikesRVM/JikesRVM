/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;


/**
 * An Expression that returns a boolean.
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public interface BooleanExpression extends Expression {

	public boolean getValue(int index);

}
