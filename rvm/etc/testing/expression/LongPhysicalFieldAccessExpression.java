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
public final class LongPhysicalFieldAccessExpression extends PhysicalFieldAccessExpression implements LongExpression {
	
	public LongPhysicalFieldAccessExpression(final TraceRecordList traceRecordList, final String fieldName) {
		super(traceRecordList, fieldName, Type.LONG);
	}
	
	public final long getValue(final int index) {
		return getTraceRecordList().getLongValue(getFieldIndex(), index);
	}
	
}
