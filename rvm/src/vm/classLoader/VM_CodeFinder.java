/*
 * (C) Copyright IBM Corp. 2001
 */
import java.util.Enumeration;

class VM_CodeFinder {

  private static boolean containsMethod(VM_CompiledMethod c, VM_Method m) {
    VM_CompilerInfo info = c.getCompilerInfo();
    switch ( info.getCompilerType() ) {

    case VM_CompilerInfo.BASELINE:
      return (c.getMethod() == m);

    case VM_CompilerInfo.OPT: 
      if (c.getMethod() == m) 
	return true;
      else {
	VM_OptCompilerInfo x = (VM_OptCompilerInfo)info;
	VM_OptMachineCodeMap map = x.getMCMap();
	int[] enc = map.inlineEncoding;
	int i = 2; 
	boolean skip = false;
	while (i < enc.length) {
	  if (enc[i] < 0) { skip = true; i++; }
	  else if (enc[i] >= 0 && skip) { skip = false; i++; }
	  else if (enc[i] >= 0 && !skip) {
	    VM_CompiledMethod z =
	      VM_ClassLoader.getCompiledMethod(enc[i]);
	    if ( z.getMethod() == m ) 
	      return true;
	    else {
	      skip = true; i++;
	    }
	  }
	}
      }
    }
    
    return false;
  }

  public static Enumeration getCompiledCode(final VM_Method sourceMethod) {
    final VM_CompiledMethod[] allCompiledCode =
      VM_ClassLoader.getCompiledMethods();

    int i;
    for(i = 0; i < allCompiledCode.length; i++) 
      if (containsMethod(allCompiledCode[i], sourceMethod))
	break;

    final int j = i;
    return new Enumeration() {
	private int index = j;
	
	public boolean hasMoreElements() {
	  return index < allCompiledCode.length;
	}

	public Object nextElement() {
	  VM_CompiledMethod x = allCompiledCode[index];
	  
	  for (; index < allCompiledCode.length; index++) 
	    if (containsMethod(allCompiledCode[index], sourceMethod))
	      break;

	  return x;
	}
      };
  }
}





