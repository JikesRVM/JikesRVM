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
public final class IntPhysicalMapExpression extends PhysicalMapExpression implements IntExpression {
	
	public IntPhysicalMapExpression(final Map map, final IntExpression key, final String fieldName) {
		super(map, key, fieldName, Type.INT);
	}
	
	public final int getValue(int index) {
		return getMap().getIntValue(getFieldIndex(), getKey().getValue(index));
	}

}
