/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.*;

/**
 * Dummy class containing enough references to force java compiler
 * to find every class comprising the opt compiler, so everything gets 
 * recompiled by just compiling "OptDummy.java".
 *
 * The minimal set has to be discovered by trial and error. Sorry. --Derek
 *
 * @author Derek Lieber
 */
class OptDummy {
  static OPT_Compiler a;
  VM_OptSaveVolatile g;
  static OPT_SpecializedMethodPool q;
}
