/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * An Expression can compute a value.
 * 
 * There are different subinterfaces depending on the type of the value computed:
 * BooleanExpression,
 * IntExpression,
 * LongExpression,
 * DoubleExpression, and
 * StringExpression.
 * 
 * There are also ByteExpression, ShortExpression and FloatExpression classes,
 * but they are only used internally to coerce physical fields
 * (which _can_ have byte, short, or float physical field types)
 * into the externally supported Types listed above.
 * 
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public interface Expression {
	
	public Type getType();
	public int getPrecedence();
	public void unparse(StringBuffer sb);
	
}
