/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
import com.ibm.JikesRVM.*;

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
    this(null, mthID, instructionOffset, address);
  }

  public breakpoint(String className, int mthID, int instructionOffset, int address) {
    this(className, mthID, instructionOffset, address, -1);
  }

  public breakpoint(String className, int mthID, int instructionOffset, int address, int lineNumber) {
    this.className   = className;
    this.methodID    = mthID;
    this.next_offset = instructionOffset;
    this.next_addr   = address;
    this.branch_addr = -1;
    this.lineNumber  = lineNumber;
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

  public String className(BootMap bmap) {
    VM_Method mth = bmap.findVMMethod(methodID, true);
    if (mth == null) {
      return className;
    }
    try {
      return mth.getDeclaringClass().getName().toString();
    } catch (Exception e) {}
    return "fuck you";
  }

  public int lineNumber(BootMap bmap) {
    try {
      return bmap.findLineNumber(methodID, next_addr);
    } catch (Exception e) {}
    return -1;
  }

  public int index(BootMap bmap) {
    return next_offset;
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
  
  public String toLongString() {
    StringBuffer sb = new StringBuffer("Breakpoint(");
    sb.append("sourceFileName="); sb.append(sourceFileName);
    sb.append(",lineNumber="); sb.append(lineNumber);
    sb.append(",className="); sb.append(className);
    sb.append(",methodID="); sb.append(methodID);
    sb.append(",next_offset="); sb.append(next_offset);
    sb.append(",next_addr="); sb.append(next_addr);
    sb.append(",next_I="); sb.append(next_I);
    sb.append(",branch_offset="); sb.append(branch_offset);
    sb.append(",branch_addr="); sb.append(branch_addr);
    sb.append(",branch_I="); sb.append(branch_I);
    sb.append(")");
    return sb.toString();
  }

}
 
