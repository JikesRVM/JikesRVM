/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.trace.Map;


/**
 * TODO
 * map[i].physicalMapFieldName
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class BytePhysicalMapExpression extends PhysicalMapExpression implements ByteExpression {
	
	public BytePhysicalMapExpression(final Map map, final IntExpression key, final String fieldName) {
		super(map, key, fieldName, Type.BYTE);
	}
	
	public final byte getValue(int index) {
		return getMap().getByteValue(getFieldIndex(), getKey().getValue(index));
	}

}
