import java.security.ProtectionDomain;
import com.ibm.oti.vm.AppClassLoader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;

class VM_ApplicationClassLoader extends AppClassLoader {

    VM_ApplicationClassLoader(ClassLoader parent) {
	super( parent );
    }

   // Set "java.class.path" property, which is used by this class
   //
   public static void setPathProperty() {
       String   classpath = null;
       String[] repositories = VM_ClassLoader.getApplicationRepositories();

       if (repositories != null)
	   for (int i = 0, n = repositories.length; i < n; ++i)
	       if (classpath == null)
		   classpath = repositories[i];
	       else
		   classpath += ":" + repositories[i];

       if (classpath == null)
	   classpath = System.getProperty("user.dir");
       
       System.setProperty("java.class.path", classpath);
   }
    

    private static byte[] getBytes(InputStream is) throws IOException {
	byte[] buf = new byte[4096];
	ByteArrayOutputStream bos = new ByteArrayOutputStream();
	int count;
	while ((count = is.read(buf)) > 0)
	    bos.write(buf, 0, count);
	return bos.toByteArray();
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

    protected Class findClass (String className) throws ClassNotFoundException
    {
	VM_Atom classDescriptor = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
	VM_Class cls = (VM_Class) VM_ClassLoader.findOrCreateType(classDescriptor, this);
	
	try {
	    URL x = findResource(classDescriptor.classFileNameFromDescriptor());

	    InputStream is = x.openConnection().getInputStream();
	    byte[] bytes = getBytes(is);

	    VM_ClassLoader.defineClassInternal(className, bytes, 0, bytes.length, this, (ProtectionDomain)getFilePD( findRepository(x.getFile()) ));

	} catch (Throwable e) {
	    throw new ClassNotFoundException(className);
	}
	
	return cls.getClassForType();
    }

}


    
		
