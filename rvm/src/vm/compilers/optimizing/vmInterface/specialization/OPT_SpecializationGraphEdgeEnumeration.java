/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import java.util.Enumeration;
/**
 * @author Julian Dolby
 */
interface OPT_SpecializationGraphEdgeEnumeration extends Enumeration {

    OPT_SpecializationGraphEdge next();

}

