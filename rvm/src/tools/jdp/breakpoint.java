/*
 * (C) Copyright IBM Corp. 2001
 */
/*
 * A breakpoint object
 * @author Ton Ngo
 */

class breakpoint
{
  String      sourceFileName;     // source file
  int         lineNumber;         // line therein (1-indexed)
  String      className;          // class corresponding to that line

  int         methodID;           // the compiled method ID

  // this is for the main breakpoint
  int         next_offset;        
  int         next_addr;          // absolute address, may be relocated
  int         next_I;		  // saved instruction 

  // this is for the branch target if current instruction is a branch (for stepping)
  int         branch_offset;      
  int         branch_addr;        
  int         branch_I;           
  
  // this is probably not used, should be deleted
  public breakpoint(String sourceFileName, int lineNumber, String className)
  {
    this.sourceFileName = sourceFileName;
    this.lineNumber     = lineNumber;
    this.className      = className;
    this.next_addr      = -1;
    this.branch_addr    = -1;
  }

  // relocatable breakpoint: with instruction offset and a method ID
  public breakpoint(int mthID, int instructionOffset, int address) {
    this.methodID    = mthID;
    this.next_offset = instructionOffset;
    this.next_addr   = address;
    this.branch_addr = -1;
  }

  // non relocatable breakpoint: raw address
  public breakpoint(int address) {
    this.methodID    = 0;
    this.next_offset = 0;
    this.next_addr   = address;
    this.branch_addr = -1;
  }

  public breakpoint()
  {
    this.sourceFileName = "";
    this.lineNumber     = 0;
    this.className      = "";
    this.next_addr      = -1;
    this.branch_addr    = -1;
  }
  
  public int address() {
    return next_addr;
  }

  /**
   * (ugly hack: pass in the bootmap pointer to be able to call its methods)
   *
   *
   */
  public String toString(BootMap bmap)
  {
    String result;

    VM_Method mth = bmap.findVMMethod(methodID, true);
    if (mth!=null) {
      String method_name = mth.getDeclaringClass().getName().toString();
      method_name += "." + mth.getName().toString();
      String line = bmap.findLineNumberAsString(methodID, next_addr);
      result = method_name +  ":" + line;
    } else {
      result = "(unknown method)";
    }

    result += " (id " + methodID + ")";

    if (branch_addr!=-1)
      return(result + 
	     " (" + Integer.toHexString(next_addr) + ":" + 
	     Integer.toHexString(next_I) + ") (" + 
	     Integer.toHexString(branch_addr) + ":" + 
	     Integer.toHexString(branch_I) + ")");
    else 
      return(result + 
	     " (" + Integer.toHexString(next_addr) + ":" + 
	     Integer.toHexString(next_I) + ")");
  }

}
 
