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
  private String currPackageName;	  // current package name, e.g. "com.ibm"
  private String currSourceFile;	  // current unqualified source file, e.g. "Foo.java"
  private String currResolvedSource; 	  // current resolved source location, e.g. "./com/ibm/Foo.java"
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
    
    resetBuffer();
  }

  /**
   * Resolves a source file given a source file name and package name
   *
   * @param packageName package name
   * @param sourceFile  unqualified name of the source file
   * @return            resolved file path of source
   */
  public final String resolveSourceFileName(String packageName, String sourceFile) {
    return sourceFinder.resolveSourceFileName(packageName, sourceFile);
  }

  /**
   * Try to get a source line from given package, source filename, and
   * line number.
   * @param packageName the package we expect the source file to be in; i.e.,
   *    the Java package specified in the source file's package declaration
   * @param sourceFile the source file, as specified in the class file
   *    (not qualified with any directory information)
   * @param linenum the line number in the source file
   * @return a string containing the source line, or a message explaining
   *    that we couldn't find it
   */
  public String getSourceLine(String packageName, String sourceFile, int linenum) {
    int start, end, j, currline;
    String resolvedSource = sourceFinder.resolveSourceFileName(packageName,sourceFile);
    //System.out.println("Resolved to: " + resolvedSource + ", previous was: " + currResolvedSource);
    
    if (!currResolvedSource.equals(resolvedSource)) {
      setCurrent(packageName, sourceFile, resolvedSource);
      data = sourceFinder.getFileContents(packageName, sourceFile);
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

  /**
   * Do given package name and source file match what we
   * currently have loaded?
   */
  public boolean sameSourceFile(String packageName, String sourceFile) {
    return currPackageName.equals(packageName) && currSourceFile.equals(sourceFile);
  }

  /**
   * Can we find a match in the filesystem for
   * given package name and source file?
   */
  public boolean exists(String packageName, String sourceFile) {
    String resolved_name = sourceFinder.resolveSourceFileName(packageName, sourceFile);
    if (resolved_name==null)
      return false;
    else
      return true;
  }
  
  /**
   * Forget what source file we currently have loaded.
   */
  public void resetBuffer() {
    setCurrent("", "", "");
  }

  /**
   * Return the fully resolved name and path of current source file.
   */
  public String getCurrentResolvedSource() {
    return currResolvedSource;
  }

  /**
   * Set current fully resolved source file.
   */
  private void setCurrent(String packageName, String sourceFile, String resolvedSource) {
    currPackageName = packageName;
    currSourceFile = sourceFile;
    currResolvedSource = resolvedSource;
  }

}
