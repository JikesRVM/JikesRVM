/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.function.IntToLongFunction;


/**
 * TODO
 * map[i].logicalMapFieldName
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public class LongLogicalMapExpression extends LogicalMapExpression implements LongExpression {

	private final IntToLongFunction mapper;
	
	
	public LongLogicalMapExpression(final String mapName, final String fieldName, final IntExpression key, final IntToLongFunction mapper) {
		super(mapName, key, fieldName, Type.LONG);
		this.mapper = mapper;
	}
	
	public final long getValue(final int index) {
		mapper.getParameter().setValue(getKey().getValue(index));
		return mapper.getValue(index);
	}
	
}
