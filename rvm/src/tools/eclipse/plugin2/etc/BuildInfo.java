/*
 * ===========================================================================
 * IBM Confidential
 * Software Group 
 *
 * Eclipse/Jikes
 *
 * (C) Copyright IBM Corp., 2002.
 *
 * The source code for this program is not published or otherwise divested of
 * its trade secrets, irrespective of what has been deposited with the U.S.
 * Copyright office.
 *
 * ==========
 * $Source$
 * $Revision$
 * $Date$
 * $Author$
 */

//package org.eclipse.jdt.internal.launching.jikesrvm2;
package com.ibm.jikesrvm.eclipse.jdt.launching;

public interface BuildInfo {
  public final static String         TIME = "@long.date@";
  public final static java.util.Date DATE = new java.util.Date(TIME);
}
