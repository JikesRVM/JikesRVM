/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.function.IntToStringFunction;


/**
 * TODO
 * map[i].logicalMapFieldName
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public class StringLogicalMapExpression extends LogicalMapExpression implements StringExpression {

	private final IntToStringFunction mapper;
	
	
	public StringLogicalMapExpression(final String mapName, final String fieldName, final IntExpression key, final IntToStringFunction mapper) {
		super(mapName, key, fieldName, Type.STRING);
		this.mapper = mapper;
	}
	
	public final String getValue(final int index) {
		mapper.getParameter().setValue(getKey().getValue(index));
		return mapper.getValue(index);
	}
	
}
