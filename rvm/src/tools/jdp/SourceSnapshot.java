/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/*
 * A string buffer that finds and holds a Java source file
 *   understand the Java file convention
 * @author Ton Ngo 
 */
import java.util.*;
import java.io.*;
import java.util.zip.*;

public class SourceSnapshot {
  static final char SOURCE_PATH_SEPARATOR = System.getProperty("path.separator").charAt(0); // eg. ";"
  private SourceFinder sourceFinder;       // to search the local file system for a source file
  private Vector sourceRepositories;
  private String currSourceName; 
  private byte data[];                    // a array of byte caching the source file last accessed
  private int line_count;

  // set up for source code access on local file system
  public SourceSnapshot() {
    // Classpath: for now all the source and class files are together
    // in the same directory, but if they are separated
    // we will need to add more paths than just the classpath

    String sourcePath = System.getProperty("java.class.path");   

    sourceRepositories = parseAndValidatePathSpecification("sourcepath", sourcePath, 
							   SOURCE_PATH_SEPARATOR);    
    sourceFinder = new SourceFinder(sourceRepositories);
    
    currSourceName = "";
  }

  public String getSourceLine(String source, int linenum) {
    int start, end, j, currline;
    //String resolvedSource = sourceFinder.resolveSourceFileName("",source);
    //System.out.println("Resolved to: " + resolvedSource + ", previous was: " + currSourceName);
    
    if (!currSourceName.equals(source)) {
      currSourceName=source;
      data = sourceFinder.getFileContents("",source);
      line_count = countLine();
    }

    if (data==null) {
      return("  (Source file not available)");
    } 
      
    // set up for first line
    currline = 1;
    j=1;
    start = 0;
    end = 0;
    for (int i=start; i<data.length; i++) {    
      if (data[i]=='\n') {
	end = i;
	break;
      }
    }

    while (end<data.length) {
      if (currline==linenum) {
	return new String(data, start, (end-start));
      } else {
	start = end+1;
	for (int i=start; i<data.length; i++) {    
	  if (data[i]=='\n') {
	    end = i;
	    currline++;
	    break;
	  }
	}
      }
    }
    
    return("Line " + linenum + " does not exist");
    

  }

  private int countLine() {
    int count = 0;
    if (data==null)
      return 0;
    for (int i=0; i<data.length; i++) {    
      if (data[i]=='\n') {
	count++;
      }
    }
    if (data[data.length-1]!='\n')   // in case last line is not terminated with newline
      count++;
    return count;
  }    

  public int getLineCount() {
    return line_count;
  }

  //*********************************************************************
  // Parse "pathSpecification" and return names of path components that actually exist.
  // A path component may be a filesystem directory, a .zip file, or a .jar file.
  // (this code is borrowed from jd for used in jdp)
  static Vector
  parseAndValidatePathSpecification(String pathKind, String pathSpecification, char pathSeparator)  {
    Vector components = new Vector();
    for (StringTokenizer st = new StringTokenizer(pathSpecification, String.valueOf(pathSeparator), false); 
	 st.hasMoreTokens(); )   {
      String componentName = st.nextToken();
      
      boolean isDuplicate = false;
      for (Enumeration c = components.elements(); c.hasMoreElements(); )
	if (componentName.equals((String)c.nextElement()))  {
	  isDuplicate = true;
	  break;
	}
         
      if (isDuplicate) {
	System.out.println(pathKind + " component \"" + componentName + "\" multiply defined (duplicate ignored)");
	continue;
      }
         
      if (componentName.endsWith(".zip") || componentName.endsWith(".jar")) {
	try {
	  ZipFile archive = new ZipFile(componentName);
	  archive.close();
	}
	catch(IOException x) { 
	  System.out.println(pathKind + " component \"" + componentName + "\" doesn't exist (ignoring it)");
	  continue;
	}
      } else {
	File dir = new File(componentName);

	if (dir.exists() == false) {
	  System.out.println(pathKind + " component \"" + componentName + "\" doesn't exist (ignoring it)");
	  continue;
	}

	if (dir.isDirectory() == false) {
	  System.out.println(pathKind + " component \"" + componentName + "\" is not a directory (ignoring it)");
	  continue;
	}
      }
         
      components.addElement(componentName);
    }
      
    return components;
  }


  public boolean sameSourceFile(String newSourceName) {
    return (currSourceName.equals(newSourceName));
  }

  public boolean exists(String name) {
    String resolved_name = sourceFinder.resolveSourceFileName("",name);
    if (resolved_name==null)
      return false;
    else
      return true;
  }
  
  public void resetBuffer() {
    currSourceName="";
  }

}
