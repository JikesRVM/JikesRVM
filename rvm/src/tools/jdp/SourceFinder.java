/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * Search local filesystem for java source files.
 * 13 Jun 1997 Derek Lieber
 * (Borrowed from jd for use in jdp)  
 * @author Ton Ngo (3/25/98)
 */

import java.util.zip.*;
import java.util.*;
import java.io.*;

public class SourceFinder  {
   static final char   FILE_PATH_SEPARATOR   = System.getProperty("file.separator").charAt(0); // eg. "\"
   private Vector repositories;
   
   SourceFinder(Vector sourceRepositories)   {
     this.repositories = sourceRepositories;
   }

  //************************************************************************
  // Resolve partially qualified source file name into fully qualified name.
  // Taken:    package name         - something like "java.lang"
  //           source name          - something like "Object.java"
  // Returned: fully qualified name - something like "d:\java\src\java\lang\Object.java"     --> local file
  //                                              or "d:\java\src.zip:java/lang/Object.java" --> local zip member
  //                                              or "d:\java\src.jar:java/lang/Object.java" --> local jar member
  //                                              or null                                    --> not found
  //
  synchronized String resolveSourceFileName(String packageName, String sourceName)  {
    String pathPrefix  = packageName.length() == 0 ? "" : packageName + ".";
    String partialName = pathPrefix.replace('.', FILE_PATH_SEPARATOR) + sourceName;

    for (Enumeration e = repositories.elements(); e.hasMoreElements(); )    {
      String repositoryName = (String)e.nextElement();

      try  {
	if (repositoryName.endsWith(".zip") || repositoryName.endsWith(".jar"))	  { // zip or jar archive
	  String   archiveMemberName = partialName.replace(FILE_PATH_SEPARATOR, '/');
	  ZipFile  archive           = new ZipFile(repositoryName);
	  ZipEntry entry             = archive.getEntry(archiveMemberName);
	  if (entry != null) {
	    archive.close();
	    return repositoryName + ":" + archiveMemberName;
	  }
	  archive.close();
	} else {                             // filesystem directory
	  String fullName = repositoryName + FILE_PATH_SEPARATOR + partialName;
	  if (new File(fullName).exists())
	    return fullName;
	}
      }
            
      catch(IOException x) {
      }
    }      
    return null;
  }

  //************************************************************************
  // Fetch source file contents, using same file resolution strategy as above.
  // Taken:    package name - something like "java.lang"
  //           source name  - something like "Object.java"
  // Returned: file contents (null --> not found)
  //
  synchronized byte[] getFileContents(String packageName, String sourceName)   {
    String pathPrefix  = packageName.length() == 0 ? "" : packageName + ".";
    String partialName = pathPrefix.replace('.', FILE_PATH_SEPARATOR) + sourceName;
    
    for (Enumeration e = repositories.elements(); e.hasMoreElements(); ) {
      String repositoryName = (String)e.nextElement();
      
      try {
	// zip or jar archive
	if (repositoryName.endsWith(".zip") || repositoryName.endsWith(".jar")) { 
	  String   archiveMemberName = partialName.replace(FILE_PATH_SEPARATOR, '/');
	  ZipFile  archive           = new ZipFile(repositoryName);
	  ZipEntry entry             = archive.getEntry(archiveMemberName);
	  
	  if (entry != null)   {
	    InputStream input  = archive.getInputStream(entry);
	    int         cnt    = (int)entry.getSize();
	    int         off    = 0;
	    byte        data[] = new byte[cnt];
	    for (;;) {
	      int n = input.read(data, off, cnt);
	      if (n <= 0)
		break;
	      off += n;
	      cnt -= n;
	    }
	    input.close();
	    archive.close();
	    return data;
	  }
	  archive.close();
	  
	}  else { // filesystem directory
	  String          fullName = repositoryName + FILE_PATH_SEPARATOR + partialName;
	  FileInputStream input    = new FileInputStream(fullName);
	  byte            data[]   = new byte[input.available()];
	  input.read(data);
	  input.close();
	  return data;
	}
      }   // try block
      
      catch(IOException x)   {
      }
    }
    
    return null;
  }

  // parse the byte array delimited by newline into an array of string
  // first element is the file name, then each line is 1-indexed
  private String[] toStringArray(String filename, byte data[]) {
    int start, end, j, numline;
    
    numline = 0;
    for (int i=0; i<data.length; i++) {
      if (data[i]=='\n')
	numline++;
    } 

    String dataArray[] = new String[numline+2];
    dataArray[0] = filename;

    // set up for first line
    start = 0;
    end = 0;
    for (int i=start; i<data.length; i++) {    
      if (data[i]=='\n') {
	end = i;
	break;
      }
    }

    j=1;
    while (end<data.length) {
      dataArray[j++] = new String(data, start, (end-start));
      start = end+1;
      for (int i=start; i<data.length; i++) {    
	if (data[i]=='\n') {
	  end = i;
	  break;
	}
      }
    }
    
    return dataArray;

  }
  
}
