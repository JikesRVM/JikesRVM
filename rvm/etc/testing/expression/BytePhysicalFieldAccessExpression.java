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
public final class BytePhysicalFieldAccessExpression extends PhysicalFieldAccessExpression implements ByteExpression {
		
	public BytePhysicalFieldAccessExpression(final TraceRecordList traceRecordList, final String fieldName) {
		super(traceRecordList, fieldName, Type.BYTE);
	}
	
	public final byte getValue(final int index) {
		return getTraceRecordList().getByteValue(getFieldIndex(), index);
	}
	
}
