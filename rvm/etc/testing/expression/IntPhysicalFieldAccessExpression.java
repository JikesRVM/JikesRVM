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
public final class IntPhysicalFieldAccessExpression extends PhysicalFieldAccessExpression implements IntExpression {
	
	public IntPhysicalFieldAccessExpression(final TraceRecordList traceRecordList, final String fieldName) {
		super(traceRecordList, fieldName, Type.INT);
	}
	
	public final int getValue(final int index) {
		return getTraceRecordList().getIntValue(getFieldIndex(), index);
	}
	
}
