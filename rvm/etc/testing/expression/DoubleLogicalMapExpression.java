/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.function.IntToDoubleFunction;


/**
 * TODO
 * map[i].logicalMapFieldName
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public class DoubleLogicalMapExpression extends LogicalMapExpression implements DoubleExpression {

	private final IntToDoubleFunction mapper;
	
	
	public DoubleLogicalMapExpression(final String mapName, final String fieldName, final IntExpression key, final IntToDoubleFunction mapper) {
		super(mapName, key, fieldName, Type.DOUBLE);
		this.mapper = mapper;
	}
	
	public final double getValue(final int index) {
		mapper.getParameter().setValue(getKey().getValue(index));
		return mapper.getValue(index);
	}
	
}
