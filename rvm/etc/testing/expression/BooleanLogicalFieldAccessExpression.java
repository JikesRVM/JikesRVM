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
public final class BooleanLogicalFieldAccessExpression extends LogicalFieldAccessExpression implements BooleanExpression {
	
	private final BooleanExpression e;
	
	
	public BooleanLogicalFieldAccessExpression(final String fieldName, final BooleanExpression e) {
		super(fieldName, e.getType());
		this.e = e;
	}
	
	public final boolean getValue(final int index) {
		return e.getValue(index);
	}
	
}
