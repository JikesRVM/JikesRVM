/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (double)f
 * 
 * Note: This type of expression is only used internally.
 * The metric expression language has no notion of float types.
 * But since the trace records can have float values (for space reasons),
 * we support the implicit conversion of float fields to doubles
 * (using the FloatToDoubleConversionExpression),
 * the access to float trace record fields
 * (using FloatFieldAccessExpression)
 * and the access to float map entry fields
 * (using MapToFloatExpression).
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class FloatToDoubleConversionExpression extends ConversionExpression implements DoubleExpression {
	
	private final FloatExpression a;
	
	
	public FloatToDoubleConversionExpression(final FloatExpression a, final boolean implicit) {
		super(a, implicit, Type.DOUBLE);
		this.a = a;
	}

	public final FloatExpression getA() {
		return a;
	}
	
	public final double getValue(final int index) {
		return (double)a.getValue(index);
	}
	
}
