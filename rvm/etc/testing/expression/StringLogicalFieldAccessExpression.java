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
public final class StringLogicalFieldAccessExpression extends LogicalFieldAccessExpression implements StringExpression {
	
	private final StringExpression e;
	
	
	public StringLogicalFieldAccessExpression(final String fieldName, final StringExpression e) {
		super(fieldName, e.getType());
		this.e = e;
	}
	
	public final String getValue(final int index) {
		return e.getValue(index);
	}
	
}
