/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.classloader.*;
/**
 * @author Julian Dolby
 */
interface OPT_SpecializationGraphNode extends OPT_GraphNode {

    VM_Method getSpecializedMethod();

}

