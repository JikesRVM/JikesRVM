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
public final class ShortPhysicalMapExpression extends PhysicalMapExpression implements ShortExpression {
	
	public ShortPhysicalMapExpression(final Map map, final IntExpression key, final String fieldName) {
		super(map, key, fieldName, Type.SHORT);
	}
	
	public final short getValue(int index) {
		return getMap().getShortValue(getFieldIndex(), getKey().getValue(index));
	}

}
