/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.function.IntToIntFunction;


/**
 * TODO
 * map[i].logicalMapFieldName
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public class IntLogicalMapExpression extends LogicalMapExpression implements IntExpression {

	private final IntToIntFunction mapper;
	
	
	public IntLogicalMapExpression(final String mapName, final String fieldName, final IntExpression key, final IntToIntFunction mapper) {
		super(mapName, key, fieldName, Type.INT);
		this.mapper = mapper;
	}
	
	public final int getValue(final int index) {
		mapper.getParameter().setValue(getKey().getValue(index));
		return mapper.getValue(index);
	}
	
}
