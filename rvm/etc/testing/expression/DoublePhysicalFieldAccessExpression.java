/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.trace.TraceRecordList;


/**
 * TODO
 * physicalTraceRecordField
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class DoublePhysicalFieldAccessExpression extends PhysicalFieldAccessExpression implements DoubleExpression {
		
	public DoublePhysicalFieldAccessExpression(final TraceRecordList traceRecordList, final String fieldName) {
		super(traceRecordList, fieldName, Type.DOUBLE);
	}
	
	public final double getValue(final int index) {
		return getTraceRecordList().getDoubleValue(getFieldIndex(), index);
	}
	
}
