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
public final class FloatPhysicalFieldAccessExpression extends PhysicalFieldAccessExpression implements FloatExpression {
		
	public FloatPhysicalFieldAccessExpression(final TraceRecordList traceRecordList, final String fieldName) {
		super(traceRecordList, fieldName, Type.FLOAT);
	}
	
	public final float getValue(final int index) {
		return getTraceRecordList().getFloatValue(getFieldIndex(), index);
	}
	
}
