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
public final class FloatPhysicalMapExpression extends PhysicalMapExpression implements FloatExpression {
	
	public FloatPhysicalMapExpression(final Map map, final IntExpression key, final String fieldName) {
		super(map, key, fieldName, Type.FLOAT);
	}
	
	public final float getValue(int index) {
		return getMap().getFloatValue(getFieldIndex(), getKey().getValue(index));
	}

}
