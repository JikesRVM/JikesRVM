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
public final class DoublePhysicalMapExpression extends PhysicalMapExpression implements DoubleExpression {
	
	public DoublePhysicalMapExpression(final Map map, final IntExpression key, final String fieldName) {
		super(map, key, fieldName, Type.DOUBLE);
	}
	
	public final double getValue(int index) {
		return getMap().getDoubleValue(getFieldIndex(), getKey().getValue(index));
	}

}
