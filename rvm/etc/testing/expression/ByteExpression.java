/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;


/**
 * An Expression that returns a byte.
 * 
 * Note: This type of expression is only used internally.
 * The metric expression language has no notion of byte types.
 * But since the trace records can have byte values (for space reasons),
 * we support the implicit conversion of bytes to ints
 * (using the ByteToIntConversionExpression),
 * the access to byte physical trace record fields
 * (using BytePhysicalFieldAccessExpression)
 * and the access to byte map entry fields
 * (using MapToByteExpression).
 * 
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public interface ByteExpression extends Expression {
	
	public byte getValue(int index);

}
