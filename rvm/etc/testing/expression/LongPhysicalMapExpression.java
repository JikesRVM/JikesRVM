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
public final class LongPhysicalMapExpression extends PhysicalMapExpression implements LongExpression {
	
	public LongPhysicalMapExpression(final Map map, final IntExpression key, final String fieldName) {
		super(map, key, fieldName, Type.LONG);
	}
	
	public final long getValue(int index) {
		return getMap().getLongValue(getFieldIndex(), getKey().getValue(index));
	}

}
