/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;


/**
 * TODO
 * logicalTraceRecordField
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class IntLogicalFieldAccessExpression extends LogicalFieldAccessExpression implements IntExpression {
	
	private final IntExpression e;
	
	
	public IntLogicalFieldAccessExpression(final String fieldName, final IntExpression e) {
		super(fieldName, e.getType());
		this.e = e;
	}
	
	public final int getValue(final int index) {
		return e.getValue(index);
	}
	
}
