/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;
import com.ibm.research.pe.model.trace.Map;


/**
 * A PhysicalMapExpression looks up a value in a physical field of a Map.
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public abstract class PhysicalMapExpression extends AbstractExpression {
	
	private final Map map;
	private final IntExpression key;
	private final String fieldName;
	private final int fieldIndex;
	private final Type type;
	
	
	public PhysicalMapExpression(final Map map, final IntExpression key, final String fieldName, final Type type) {
		this.map = map;
		this.key = key;
		this.fieldName = fieldName;
		this.fieldIndex = map.getMapEntryType().getPhysicalFieldIndex(fieldName);
		this.type = type;
	}

	public final Type getType() {
		return type;
	}
	
	public final int getPrecedence() {
		return Precedence.POSTFIX;
	}
	
	public final void unparse(final StringBuffer sb) {
		sb.append(map.getName());
		sb.append("[");
		key.unparse(sb);
		sb.append("].");
		sb.append(fieldName);
	}

	public final String getMapName() {
		return map.getName();
	}

	public final IntExpression getKey() {
		return key;
	}
	
	public final String getFieldName() {
		return fieldName;
	}
	
	public final Map getMap() {
		return map;
	}
	
	public final int getFieldIndex() {
		return fieldIndex;
	}
	
}
