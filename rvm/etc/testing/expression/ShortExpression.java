/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;



/**
 * An Expression that returns a short.
 * 
 * Note: This type of expression is only used internally.
 * The metric expression language has no notion of short types.
 * But since the trace records can have short values (for space reasons),
 * we support the implicit conversion of shorts to ints
 * (using the ShortToIntConversionExpression),
 * the access to short physical trace record fields
 * (using ShortPhysicalFieldAccessExpression)
 * and the access to short map entry fields
 * (using MapToShortExpression).
 * 
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public interface ShortExpression extends Expression {
	
	public short getValue(int index);

}
