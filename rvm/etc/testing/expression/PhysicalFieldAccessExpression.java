/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;
import com.ibm.research.pe.model.trace.TraceRecordList;


/**
 * A PhysicalFieldAccessExpression accesses a physical field in a trace record.
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public abstract class PhysicalFieldAccessExpression extends AbstractExpression {
	
	private final TraceRecordList traceRecordList;
	private final String fieldName;
	private final int fieldIndex;
	private final Type type;
	
	
	public PhysicalFieldAccessExpression(final TraceRecordList traceRecordList, final String fieldName, final Type type) {
		this.traceRecordList = traceRecordList;
		this.fieldName = fieldName;
		this.fieldIndex = traceRecordList.getTraceRecordType().getPhysicalFieldIndex(fieldName);
		this.type = type;
	}
	
	public final Type getType() {
		return type;
	}
	
	public final int getPrecedence() {
		return Precedence.ATOM;
	}

	public final void unparse(final StringBuffer sb) {
		sb.append(fieldName);
	}

	public final String getFieldName() {
		return fieldName;
	}
	
	public final TraceRecordList getTraceRecordList() {
		return traceRecordList;
	}
	
	public final int getFieldIndex() {
		return fieldIndex;
	}
	
}
