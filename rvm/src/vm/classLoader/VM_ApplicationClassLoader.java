/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * @author Julian Dolby
 * @date May 20, 2002
 */

import java.security.ProtectionDomain;
//-#if RVM_WITH_GNU_CLASSPATH
//-#else
 import com.ibm.oti.vm.AppClassLoader;
//-#endif
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.net.URL;

//-#if RVM_WITH_GNU_CLASSPATH
class VM_ApplicationClassLoader extends ClassLoader {
//-#else
class VM_ApplicationClassLoader extends AppClassLoader {
//-#endif

    VM_ApplicationClassLoader(ClassLoader parent) {
	super( parent );
    }

   // Set "java.class.path" property, which is used by this class
   //
   public static void setPathProperty() {
       String   classpath = null;
       String[] repositories = VM_ClassLoader.getApplicationRepositories();

       if (repositories != null)
	   for (int i = 0, n = repositories.length; i < n; ++i) {
	       String name = repositories[i];
	       if (name.startsWith("."))
		   name =
		       System.getProperty("user.dir") +
		       File.separator +
		       name.substring(1);
 
	       if (classpath == null)
		   classpath = name;
	       else
		   classpath += File.pathSeparator + name;
	   }

       if (classpath == null)
	   classpath = System.getProperty("user.dir");
       
       System.setProperty("java.class.path", classpath);
   }
    

    private String findRepository(String resourceName) {
	for(int i = 0; i < parsedPath.length; i++) 
	    if (resourceName.startsWith(parsedPath[i]))
		return parsedPath[i];
	    else if (resourceName.startsWith( toURLString(parsedPath[i]) ))
		return parsedPath[i];

	VM.sysWrite("Cannot find repository for " + resourceName + "\n");
	throw new Error();
    }

    protected Class findClass (String className) throws ClassNotFoundException {
      VM_Atom classDescriptor = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
      VM_Class cls = (VM_Class) VM_ClassLoader.findOrCreateType(classDescriptor, this);
	
      try {
        URL x = findResource(classDescriptor.classFileNameFromDescriptor());
        InputStream is = x.openConnection().getInputStream();
        //-#if RVM_WITH_GNU_CLASSPATH
        VM_ClassLoader.defineClassInternal(className, is, this, null /*(ProtectionDomain)getFilePD(findRepository(x.getFile())));*/ );
        //-#else
        VM_ClassLoader.defineClassInternal(className, is, this, (ProtectionDomain)getFilePD(findRepository(x.getFile())));
        //-#endif

      } catch (Throwable e) {
	throw new ClassNotFoundException(className);
      }
	
      return cls.getClassForType();
    }

}


    
		
