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
public final class StringPhysicalFieldAccessExpression extends PhysicalFieldAccessExpression implements StringExpression {
		
	public StringPhysicalFieldAccessExpression(final TraceRecordList traceRecordList, final String fieldName) {
		super(traceRecordList, fieldName, Type.STRING);
	}
	
	public final String getValue(final int index) {
		return getTraceRecordList().getStringValue(getFieldIndex(), index);
	}
	
}
