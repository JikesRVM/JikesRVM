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
public final class DoubleLogicalFieldAccessExpression extends LogicalFieldAccessExpression implements DoubleExpression {
	
	private final DoubleExpression e;
	
	
	public DoubleLogicalFieldAccessExpression(final String fieldName, final DoubleExpression e) {
		super(fieldName, e.getType());
		this.e = e;
	}
	
	public final double getValue(final int index) {
		return e.getValue(index);
	}
	
}
