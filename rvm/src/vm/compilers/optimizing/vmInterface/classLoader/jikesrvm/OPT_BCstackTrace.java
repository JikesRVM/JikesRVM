/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;

/**
 * @author Ton Ngo
 */
class OPT_BCstackTrace {
  VM_Method methodAtOffset;
  VM_Method sourceMethod;
  VM_Method actualMethod;
  int byteCodeOffset;

  public String toString() {
    StringBuffer s = new StringBuffer();
    s.append("Method: ");
    s.append(sourceMethod);
    s.append("@");
    s.append(byteCodeOffset);
    if (actualMethod != sourceMethod) {
      s.append(" (inlined into ");
      s.append(actualMethod);
      s.append(")");
    }
    if (sourceMethod != methodAtOffset) {
      s.append(" (at current offset ");
      s.append(methodAtOffset);
      s.append(")");
    }
    return  s.toString();
  }

  public boolean equals(Object o) {
    if (o instanceof OPT_BCstackTrace) {
      OPT_BCstackTrace s = (OPT_BCstackTrace)o;
      return  (sourceMethod == s.sourceMethod && byteCodeOffset == 
          s.byteCodeOffset);
    } 
    else 
      return  false;
  }

  static OPT_BCstackTrace[] reconstituteBCstackTrace(VM_StackTrace[] trace) {
    int BCLength = 0;
    for (int i = 0; i < trace.length; i++) {
      VM_CompiledMethod m = trace[i].compiledMethod;
      if (m == null)
        continue;
      if (m.getCompilerType() == VM_CompiledMethod.OPT) {
        VM_OptCompiledMethod opt = (VM_OptCompiledMethod)cm;
        VM_OptMachineCodeMap map = opt.getMCMap();
        int[] tree = map.inlineEncoding;
        for (int j = map.getInlineEncodingForMCOffset
            (trace[i].instructionOffset); j >= 0; BCLength++, 
            j = VM_OptEncodedCallSiteTree.getParent(j, tree));
      } else if (m.getCompilerType() == VM_CompiledMethod.BASELINE) {
	  BCLength++;
      }
    }
    OPT_BCstackTrace[] bcTrace = new OPT_BCstackTrace[BCLength];
    int index = 0;
    for (int i = 0; i < trace.length; i++) {
      VM_CompiledMethod m = trace[i].compiledMethod;
      if (m == null) continue;
      int offset = trace[i].instructionOffset;
      if (m.getCompilerType() == VM_CompiledMethod.OPT) {
        VM_OptMachineCodeMap map = (VM_OptCompiledMethod)cm.getMCMap();
        int[] tree = map.inlineEncoding;
        int bcOffset = map.getBytecodeIndexForMCOffset(offset);
        VM_Method bin = map.getMethodForMCOffset(offset);
        for (int j = map.getInlineEncodingForMCOffset(offset); j >= 0; index++, 
            j = VM_OptEncodedCallSiteTree.getParent(j, tree)) {
          bcTrace[index] = new OPT_BCstackTrace();
          bcTrace[index].actualMethod = m.getMethod();
          bcTrace[index].methodAtOffset = bin;
          bcTrace[index].sourceMethod = VM_MethodDictionary.getValue
              (VM_OptEncodedCallSiteTree.getMethodID(j, tree));
          bcTrace[index].byteCodeOffset = bcOffset;
          if (j > 0)
            bcOffset = VM_OptEncodedCallSiteTree.getByteCodeOffset(j, 
                tree);
        }
      } else if (m.getCompilerType() == VM_CompiledMethod.BASELINE) {
	  VM_BaselineCompiledMethod bm = (VM_BaselineCompiledMethod)m;
	  OPT_BCstackTrace bcs = new OPT_BCstackTrace();
	  bcs.actualMethod = m.getMethod();
	  bcs.sourceMethod = m.getMethod();
	  bcs.methodAtOffset = m.getMethod();
	  if (VM.BuildForIA32)
	      bcs.byteCodeOffset = bm.findBytecodeIndexForInstruction(offset);
	  else
	      bcs.byteCodeOffset = bm.findBytecodeIndexForInstruction(offset>>2);
	  bcTrace[index++] = bcs;
      }
    }
    return  bcTrace;
  }
}




