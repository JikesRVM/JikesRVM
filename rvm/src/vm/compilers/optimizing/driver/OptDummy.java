/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
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
