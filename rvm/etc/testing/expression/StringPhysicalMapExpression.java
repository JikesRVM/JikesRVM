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
public final class StringPhysicalMapExpression extends PhysicalMapExpression implements StringExpression {
	
	public StringPhysicalMapExpression(final Map map, final IntExpression key, final String fieldName) {
		super(map, key, fieldName, Type.STRING);
	}
	
	public final String getValue(int index) {
		return getMap().getStringValue(getFieldIndex(), getKey().getValue(index));
	}

}
