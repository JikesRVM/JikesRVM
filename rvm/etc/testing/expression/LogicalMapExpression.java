/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;


/**
 * TODO
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public abstract class LogicalMapExpression extends AbstractExpression {
	
	private final String mapName;
	private final IntExpression key;
	private final String fieldName;
	private final Type type;
	
	
	public LogicalMapExpression(final String mapName, final IntExpression key, final String fieldName, final Type type) {
		this.mapName = mapName;
		this.key = key;
		this.fieldName = fieldName;
		this.type = type;
	}
	
	public final Type getType() {
		return type;
	}
	
	public final int getPrecedence() {
		return Precedence.POSTFIX;
	}
	
	public final void unparse(final StringBuffer sb) {
		sb.append(mapName);
		sb.append("[");
		key.unparse(sb);
		sb.append("].");
		sb.append(fieldName);
	}

	public final String getMapName() {
		return mapName;
	}

	public final IntExpression getKey() {
		return key;
	}
	
	public final String getFieldName() {
		return fieldName;
	}
	
}
