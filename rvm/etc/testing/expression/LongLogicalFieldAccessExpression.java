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
public final class LongLogicalFieldAccessExpression extends LogicalFieldAccessExpression implements LongExpression {
	
	private final LongExpression e;
	
	
	public LongLogicalFieldAccessExpression(final String fieldName, final LongExpression e) {
		super(fieldName, e.getType());
		this.e = e;
	}
	
	public final long getValue(final int index) {
		return e.getValue(index);
	}
	
}
