/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;


/**
 * TODO
 * (int)s
 * 
 * Note: This type of expression is only used internally.
 * The metric expression language has no notion of short types.
 * But since the trace records can have short values (for space reasons),
 * we support the implicit conversion of short fields to ints
 * (using the ShortToIntConversionExpression),
 * the access to short trace record fields
 * (using ShortFieldAccessExpression)
 * and the access to short map entry fields
 * (using MapToShortExpression).
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class ShortToIntConversionExpression extends ConversionExpression implements IntExpression {
	
	private final ShortExpression a;
	
	
	public ShortToIntConversionExpression(final ShortExpression a, final boolean implicit) {
		super(a, implicit, Type.INT);
		this.a = a;
	}

	public final ShortExpression getA() {
		return a;
	}
	
	public final int getValue(final int index) {
		return (int)a.getValue(index);
	}
	
}
