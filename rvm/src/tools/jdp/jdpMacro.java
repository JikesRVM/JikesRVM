/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/*
 *   A string buffer that holds jdp command for macro processing
 *   This can be nested for nested macro
 *   A macro is loaded and read once only
 *   At the end, further read will return false
 *   This is extended from SourceSnapshot so macro file
 *   can reside anywhere in the standard java class path
 * @author Ton Ngo
 */
import java.util.*;
import java.io.*;
import java.util.zip.*;

public class jdpMacro extends SourceSnapshot {
  private int curr_line = 0;
  private String name;
  private String cmd;
  private String args[];

  public jdpMacro() {
    super();
  }

  public void load(String macroName) {
    name = macroName;
    curr_line = 0;
    String myline = super.getSourceLine(macroName, 1);
    // System.out.println("Loading macro: " + macroName);
    // System.out.println("Got: " + myline);
  }
  
  public boolean next() {

    if (curr_line==super.getLineCount()) {
      super.resetBuffer();
      return false;
    } else {

      curr_line++;

      // parse line into words
      //
      String str = new String(super.getSourceLine(name, curr_line));
      while (str.startsWith("#")) {
	if (curr_line==super.getLineCount()) {
	  super.resetBuffer();
	  return false;
	} else {
	  curr_line++;
	  str = new String(super.getSourceLine(name, curr_line));
	}
      }

      Vector vec = new Vector();
      for (;;)  {
	while (str.startsWith(" "))
	  str = str.substring(1);          // skip blanks
	if (str.length() == 0)
	  break;                           // end of string
	int end = str.indexOf(" ");
	if (end == -1) {
	  vec.addElement(str);
	  str = "";
	}  else  {
	  vec.addElement(str.substring(0, end));
	  str = str.substring(end);
	}
      }

      // if empty line, keep the last command
      if (vec.size() != 0)  {
	cmd  = (String)vec.elementAt(0);
	args = new String[vec.size() - 1];
	for (int i = 0; i < args.length; ++i)
	  args[i] = (String)vec.elementAt(i + 1);
      }
    }
    return true;
  }

  public String cmd() {
    return cmd;
  }

  public String[] args() {
    return args;
  }

  public int currentLine() {
    return curr_line;
  }

}
