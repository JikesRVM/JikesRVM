/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;



/**
 * An Expression that returns a float.
 * 
 * Note: This type of expression is only used internally.
 * The metric expression language has no notion of float types.
 * But since the trace records can have float values (for space reasons),
 * we support the implicit conversion of floats to doubles
 * (using the FloatToDoubleConversionExpression),
 * the access to float physical trace record fields
 * (using FloatPhysicalFieldAccessExpression)
 * and the access to float map entry fields
 * (using MapToFloatExpression).
 * 
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public interface FloatExpression extends Expression {
	
	public float getValue(int index);

}
