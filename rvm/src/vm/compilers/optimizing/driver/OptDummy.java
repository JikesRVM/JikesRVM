/*
 * (C) Copyright IBM Corp. 2001
 */
// Dummy class containing enough references to force java compiler
// to find every class comprising the opt compiler, so everything gets recompiled
// by just compiling "OptDummy.java".
//
// The minimal set has to be discovered by trial and error. Sorry. --Derek
//
class OptDummy {
  static OPT_Compiler a;
  static OPT_ContextFreeInlineOracle d;
  static OPT_CCInlineOracle e;
  static OPT_StaticInlineOracle f;
  VM_OptSaveVolatile g;
  //-#if RVM_WITH_CONCURRENT_GC
  VM_OptRCWriteBarrier i;
  //-#endif
  static OPT_SpecializedMethodPool q;
}
