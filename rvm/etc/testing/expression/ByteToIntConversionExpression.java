/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (int)by
 * 
 * Note: This type of expression is only used internally.
 * The metric expression language has no notion of byte types.
 * But since the trace records can have byte values (for space reasons),
 * we support the implicit conversion of byte fields to ints
 * (using the ByteToIntConversionExpression),
 * the access to byte trace record fields
 * (using ByteFieldAccessExpression)
 * and the access to byte map entry fields
 * (using MapToByteExpression).
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class ByteToIntConversionExpression extends ConversionExpression implements IntExpression {
	
	private final ByteExpression a;
	
	
	public ByteToIntConversionExpression(final ByteExpression a, final boolean implicit) {
		super(a, implicit, Type.INT);
		this.a = a;
	}

	public final ByteExpression getA() {
		return a;
	}
	
	public final int getValue(final int index) {
		return (int)a.getValue(index);
	}
	
}
