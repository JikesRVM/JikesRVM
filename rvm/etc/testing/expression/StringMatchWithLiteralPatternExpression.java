/*
 * (C) Copyright IBM Corp. 2004
 */
// $Id$
package com.ibm.research.pe.model.metric.expression;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.research.pe.model.metric.Type;
import com.ibm.research.pe.model.metric.Precedence;


/**
 * TODO
 * s=~s
 *
 * @author Matthias.Hauswirth@Colorado.EDU
 */
public final class StringMatchWithLiteralPatternExpression extends BinaryExpression implements BooleanExpression {
	
	private final StringExpression a;
	private final StringLiteralExpression b;
	private final Pattern pattern;
	
	
	public StringMatchWithLiteralPatternExpression(final StringExpression a, final StringLiteralExpression b) {
		super(a, b, Type.BOOLEAN, "=~", Precedence.EQUALITY);
		this.a = a;
		this.b = b;
		final String patternString = b.getValue(-1);
		pattern = Pattern.compile(patternString);
	}

	public final StringExpression getA() {
		return a;
	}
	
	public final StringExpression getB() {
		return b;
	}
	
	public final boolean getValue(final int index) {
		final String text = a.getValue(index);
		final Matcher matcher = pattern.matcher(text);
		return matcher.find();
	}
	
}
