/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.function.IntToBooleanFunction;


/**
 * TODO
 * map[i].logicalMapFieldName
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public class BooleanLogicalMapExpression extends LogicalMapExpression implements BooleanExpression {

	private final IntToBooleanFunction mapper;
	
	
	public BooleanLogicalMapExpression(final String mapName, final String fieldName, final IntExpression key, final IntToBooleanFunction mapper) {
		super(mapName, key, fieldName, Type.BOOLEAN);
		this.mapper = mapper;
	}
	
	public final boolean getValue(final int index) {
		mapper.getParameter().setValue(getKey().getValue(index));
		return mapper.getValue(index);
	}
	
}
