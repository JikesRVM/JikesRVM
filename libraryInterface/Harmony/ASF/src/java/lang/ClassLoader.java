/* 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.lang;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.NoSuchElementException;
import java.util.Vector;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.runtime.DynamicLibrary;

import org.apache.harmony.lang.RuntimePermissionCollection;

/**
 * <p>
 * A ClassLoader is used for loading classes.
 * </p>
 * 
 * <h4>VM Implementors Note</h4>
 * <p>
 * This class must be implemented by the VM. The documented methods and natives
 * must be implemented to support other provided class implementations in this
 * package.
 * </p>
 * 
 * @since 1.0
 * @see Class
 */
public abstract class ClassLoader {

    /**
     * Print extra debug info.
     */
    private static final boolean DEBUG = false;

    /**
     * empty set of certificates
     */
    private static final Certificate[] EMPTY_CERTIFICATES = new Certificate[0];

    /**
     * default protection domain.
     */
    private ProtectionDomain defaultDomain;
  
    /**
     * Map containing all packages defined in the class loader
     */
    private final HashMap<String, Package> definedPackages;

    /**
     * Map containing all classes loaded by the class loader
     */
    private final HashMap<String, Class<?>> loadedClasses;

    /**
     * The following
     * mapping is used <String name, Certificate[] certificates>, where name -
     * the name of a package, certificates - array of certificates.
     */
    private final Hashtable<String, Certificate[]> packageCertificates;

    /**
     * The 'System' ClassLoader; also known as the bootstrap ClassLoader.
     * 
     * @see #getSystemClassLoader()
     */
    private static ClassLoader systemClassLoader;

    /**
     * parent class loader
     */
    private final ClassLoader parentClassLoader;

    /**
     * Constructs a new instance of this class with the system class loader as
     * its parent.
     * 
     * @throws SecurityException
     *             if a security manager exists and it does not allow the
     *             creation of new ClassLoaders.
     */
    protected ClassLoader() {
	this(getSystemClassLoader());
    }

    /**
     * Constructs a new instance of this class with the given class loader as
     * its parent.
     * 
     * @param parentLoader
     *            The ClassLoader to use as the new class loaders parent.
     * @throws SecurityException
     *             if a security manager exists and it does not allow the
     *             creation of new ClassLoaders.
     * @throws NullPointerException
     *             if the parent is null.
     */
    protected ClassLoader(ClassLoader parentLoader) {
	SecurityManager sc = System.getSecurityManager();
        if (sc != null) {
            sc.checkCreateClassLoader();
        }
        parentClassLoader = parentLoader;
        definedPackages = new HashMap<String, Package>();
        loadedClasses = new HashMap<String, Class<?>>();
        packageCertificates =  new Hashtable<String, Certificate[]>();
    }

    /**
     * <p>
     * TODO Document this method.
     * </p>
     */
    static final void initializeClassLoaders() {
        return;
    }

    /**
     * Returns the system class loader. This is the parent for new ClassLoader
     * instances, and is typically the class loader used to start the
     * application. If a security manager is present, and the caller's class
     * loader is not null and the caller's class loader is not the same as or an
     * ancestor of the system class loader, then this method calls the security
     * manager's checkPermission method with a
     * RuntimePermission("getClassLoader") permission to ensure it's ok to
     * access the system class loader. If not, a SecurityException will be
     * thrown.
     * 
     * @return The system classLoader.
     * @throws SecurityException
     *             if a security manager exists and it does not allow access to
     *             the system class loader.
     */
    public static ClassLoader getSystemClassLoader() {
        SecurityManager sc = System.getSecurityManager();
        if (sc != null) {
            // we use VMClassRegistry.getClassLoader(...) method instead of
            // Class.getClassLoader() due to avoid redundant security
            // checking
            ClassLoader callerLoader = RVMClass.getClassLoaderFromStackFrame(1);
            if (callerLoader != null && callerLoader != systemClassLoader) {
                sc.checkPermission(RuntimePermissionCollection.GET_CLASS_LOADER_PERMISSION);
            }
        }
        return BootstrapClassLoader.getBootstrapClassLoader();
    }

    /**
     * Answers an URL specifying a resource which can be found by looking up
     * resName using the system class loader's resource lookup algorithm.
     * 
     * @return A URL specifying a system resource or null.
     * @param resName
     *            The name of the resource to find.
     * @see Class#getResource
     */
    public static URL getSystemResource(String resName) {
	return getSystemClassLoader().getResource(resName);
    }

    /**
     * Answers an Enumeration of URLs containing all resources which can be
     * found by looking up resName using the system class loader's resource
     * lookup algorithm.
     * 
     * @return An Enumeration of URLs containing the system resources
     * @param resName
     *            String the name of the resource to find.
     * @throws IOException
     *             if an IO exception occurs
     */
    public static Enumeration<URL> getSystemResources(String resName)
	throws IOException {
	return getSystemClassLoader().getResources(resName);
    }

    /**
     * Answers a stream on a resource found by looking up resName using the
     * system class loader's resource lookup algorithm. Basically, the contents
     * of the java.class.path are searched in order, looking for a path which
     * matches the specified resource.
     * 
     * @return A stream on the resource or null.
     * @param resName
     *            The name of the resource to find.
     * @see Class#getResourceAsStream
     */
    public static InputStream getSystemResourceAsStream(String resName) {
	return getSystemClassLoader().getResourceAsStream(resName);
    }

    /**
     * Constructs a new class from an array of bytes containing a class
     * definition in class file format.
     * 
     * @param classRep
     *            A memory image of a class file.
     * @param offset
     *            The offset into the classRep.
     * @param length
     *            The length of the class file.
     * @deprecated Use defineClass(String, byte[], int, int)
     */
    @Deprecated
    protected final Class<?> defineClass(byte[] classRep, int offset, int length)
	throws ClassFormatError {
	return defineClass(null, classRep, offset, length);
    }

    /**
     * Constructs a new class from an array of bytes containing a class
     * definition in class file format.
     * 
     * @param className
     *            The name of the new class
     * @param classRep
     *            A memory image of a class file
     * @param offset
     *            The offset into the classRep
     * @param length
     *            The length of the class file
     */
    protected final Class<?> defineClass(String className, byte[] classRep,
					 int offset, int length) throws ClassFormatError {
        return defineClass(className, classRep, offset, length, null);
    }

    /**
     * Constructs a new class from an array of bytes containing a class
     * definition in class file format and assigns the new class to the
     * specified protection domain.
     * 
     * @param className
     *            The name of the new class.
     * @param classRep
     *            A memory image of a class file.
     * @param offset
     *            The offset into the classRep.
     * @param length
     *            The length of the class file.
     * @param protectionDomain
     *            The protection domain this class should belongs to.
     */
    protected final Class<?> defineClass(String className, byte[] classRep,
					 int offset, int length, ProtectionDomain protectionDomain)
	throws java.lang.ClassFormatError {
        if (className != null && className.indexOf('/') != -1) {
            throw new NoClassDefFoundError(
		"The name is expected in binary (canonical) form,"
		+ " therefore '/' symbols are not allowed: " + className);
        }
        if (offset < 0 || length < 0 || offset + length > classRep.length) {
            throw new IndexOutOfBoundsException(
                "Either offset or len is outside of the data array");
        }
        if (protectionDomain == null) {
            if (defaultDomain == null) {
                defaultDomain = new ProtectionDomain(
		    new CodeSource(null, (Certificate[])null), null, this, null);            
            }        
            protectionDomain = defaultDomain;
        }
        Certificate[] certs = null;
        String packageName = null;
        if (className != null) {
            if (className.startsWith("java.")) {
                throw new SecurityException(
                    "It is not allowed to define classes inside the java.* package: " + className);
            }
            int lastDot = className.lastIndexOf('.');
            packageName = lastDot == -1 ? "" : className.substring(0, lastDot);
            certs = getCertificates(packageName, protectionDomain.getCodeSource());
        }
        Class<?> clazz = defineClass0(className, classRep, offset, length);
        clazz.setProtectionDomain(protectionDomain);
        if (certs != null) {
            packageCertificates.put(packageName, certs);
        }
        return clazz;
    }
  
    /**
     * Loads new type into the classloader name space. 
     * The class loader is marked as defining class loader. 
     */
    private Class<?> defineClass0(String name, byte[] data, int offset, int len) 
	throws ClassFormatError {
	RVMType vmType = RVMClassLoader.defineClassInternal(name, data, offset, len, this);
	Class<?> ans = vmType.getClassForType();
	loadedClasses.put(name, ans);
	return ans;
    }

    /**
     * <p>
     * Defines a new class for the name, bytecodes in the byte buffer and the
     * protection domain.
     * </p>
     * 
     * @param name
     *            The name of the class to define.
     * @param b
     *            The byte buffer containing the bytecodes of the new class.
     * @param protectionDomain
     *            The protection domain this class belongs to.
     * @return The defined class.
     * @throws ClassFormatError
     *             if an invalid class file is defined.
     * @since 1.5
     */
    protected final Class<?> defineClass(String name, ByteBuffer b,
					 ProtectionDomain protectionDomain) throws ClassFormatError {
        byte[] temp = new byte[b.remaining()];
        b.get(temp);
        return defineClass(name, temp, 0, temp.length, protectionDomain);
    }

    /**
     * Overridden by subclasses, by default throws ClassNotFoundException. This
     * method is called by loadClass() after the parent ClassLoader has failed
     * to find a loaded class of the same name.
     * 
     * @return The class or null.
     * @param className
     *            The name of the class to search for.
     * @throws ClassNotFoundException
     *             if the class cannot be found.
     */
    protected Class<?> findClass(String className)
	throws ClassNotFoundException {
        throw new ClassNotFoundException("Can not find class " + className);
    }

    /**
     * Attempts to find and return a class which has already been loaded by the
     * virtual machine. Note that the class may not have been linked and the
     * caller should call resolveClass() on the result if necessary.
     * 
     * @return The class or null.
     * @param className
     *            The name of the class to search for.
     */
    protected final Class<?> findLoadedClass(String className) {
	return loadedClasses.get(className);
    }

    /**
     * Attempts to load a class using the system class loader. Note that the
     * class has already been been linked.
     * 
     * @return The class which was loaded.
     * @param className
     *            The name of the class to search for.
     * @throws ClassNotFoundException
     *             if the class cannot be found.
     */
    protected final Class<?> findSystemClass(String className)
	throws ClassNotFoundException {
        return getSystemClassLoader().loadClass(className, false);
    }

    /**
     * Returns the specified ClassLoader's parent.
     * 
     * @return The class or null.
     * @throws SecurityException
     *             if a security manager exists and it does not allow the parent
     *             loader to be retrieved.
     */
    public final ClassLoader getParent() {
        SecurityManager sc = System.getSecurityManager();
        if (sc != null) {
            ClassLoader callerLoader = RVMClass.getClassLoaderFromStackFrame(1);
            if (callerLoader != null && !callerLoader.isSameOrAncestor(this)) {
                sc.checkPermission(RuntimePermissionCollection.GET_CLASS_LOADER_PERMISSION);
            }
        }
        return parentClassLoader;
    }

    /**
     * Answers an URL which can be used to access the resource described by
     * resName, using the class loader's resource lookup algorithm. The default
     * behavior is just to return null.
     * 
     * @return The location of the resource.
     * @param resName
     *            String the name of the resource to find.
     * @see Class#getResource(String)
     */
    public URL getResource(String resName) {
        String nm = resName.toString(); // NPE if null
        URL foundResource = (parentClassLoader == null)
            ? BootstrapClassLoader.getBootstrapClassLoader().findResource(nm)
            : parentClassLoader.getResource(nm);
        return foundResource == null ? findResource(nm) : foundResource;
    }

    /**
     * Answers an Enumeration of URL which can be used to access the resources
     * described by resName, using the class loader's resource lookup algorithm.
     * The default behavior is just to return an empty Enumeration.
     * 
     * @return The location of the resources.
     * @param resName
     *            String the name of the resource to find.
     * @throws IOException
     *             if an IO exception occurs
     */
    public Enumeration<URL> getResources(String resName) throws IOException {
	if (DEBUG) org.jikesrvm.VM.sysWriteln("getResources " + resName);
        ClassLoader cl = this;
        final ArrayList<Enumeration<URL>> foundResources = 
            new ArrayList<Enumeration<URL>>();
        Enumeration<URL> resourcesEnum;
        do {
            resourcesEnum = cl.findResources(resName);
            if (resourcesEnum != null && resourcesEnum.hasMoreElements()) {
                foundResources.add(resourcesEnum);
            }            
        } while ((cl = cl.parentClassLoader) != null);
        resourcesEnum = BootstrapClassLoader.getBootstrapClassLoader().findResources(resName);
        if (resourcesEnum != null && resourcesEnum.hasMoreElements()) {
            foundResources.add(resourcesEnum);
        }
        return new Enumeration<URL>() {
	    private int position = foundResources.size() - 1;
	    public boolean hasMoreElements() {
		while (position >= 0) {
		    if (foundResources.get(position).hasMoreElements()) {
			return true;
		    }
		    position--;
		}
		return false;
	    }

	    public URL nextElement() {
		while (position >= 0) {
		    try {
			return (foundResources.get(position)).nextElement();
		    } catch (NoSuchElementException e) {}
		    position--;
		}
		throw new NoSuchElementException();
	    }
	};
    }

    /**
     * Answers a stream on a resource found by looking up resName using the
     * class loader's resource lookup algorithm. The default behavior is just to
     * return null.
     * 
     * @return A stream on the resource or null.
     * @param resName
     *            String the name of the resource to find.
     * @see Class#getResourceAsStream
     */
    public InputStream getResourceAsStream(String resName) {
	URL foundResource = getResource(resName);
        if (foundResource != null) {
            try {
                return foundResource.openStream();
            } catch (IOException e) {
            }
        }
        return null;
    }

    /**
     * Invoked by the Virtual Machine when resolving class references.
     * Equivalent to loadClass(className, false);
     * 
     * @return The Class object.
     * @param className
     *            The name of the class to search for.
     * @throws ClassNotFoundException
     *             if the class could not be found.
     */
    public Class<?> loadClass(String className) throws ClassNotFoundException {
	return loadClass(className, false);
    }
  
    /**
     * Loads the class with the specified name, optionally linking the class
     * after load. Steps are: 1) Call findLoadedClass(className) to determine if
     * class is loaded 2) Call loadClass(className, resolveClass) on the parent
     * loader. 3) Call findClass(className) to find the class
     * 
     * @return The Class object.
     * @param className
     *            The name of the class to search for.
     * @param resolveClass
     *            Indicates if class should be resolved after loading.
     * @throws ClassNotFoundException
     *             if the class could not be found.
     */
    protected Class<?> loadClass(String className, boolean resolveClass)
	throws ClassNotFoundException {
	if (className == null) {
            throw new NullPointerException();
        }
        if(className.indexOf("/") != -1) {
            throw new ClassNotFoundException(className);
        }

        Class<?> clazz = findLoadedClass(className);
        if (clazz == null) {
            if (parentClassLoader == null) {
                clazz = BootstrapClassLoader.getBootstrapClassLoader().loadClass(className, false);
            } else {
                try {
                    clazz = parentClassLoader.loadClass(className);
                    // NB DRLVM does a trick here to determine the class loader
                    // for a class, for Jikes RVM this is unnecessary as we have
                    // the class loader in the type reference already pulled
                    // from the stack, bytecode or specified
                } catch (ClassNotFoundException e) {
                }
            }
            if (clazz == null) {
                clazz = findClass(className);
                if (clazz == null) {
                    throw new ClassNotFoundException(className);
                }
            }
        }
        if (resolveClass) {
            resolveClass(clazz);
        }
        return clazz;
    }

    /**
     * Forces a class to be linked (initialized). If the class has already been
     * linked this operation has no effect.
     * 
     * @param clazz
     *            The Class to link.
     * @throws NullPointerException
     *             if clazz is null.
     */
    protected final void resolveClass(Class<?> clazz) {
	RVMType cls = JikesRVMSupport.getTypeForClass(clazz);
	cls.resolve();
	cls.instantiate();
	cls.initialize();
    }

    /**
     * This method must be provided by the VM vendor, as it is used by other
     * provided class implementations in this package. A sample implementation
     * of this method is provided by the reference implementation. This method
     * is used by SecurityManager.classLoaderDepth(), currentClassLoader() and
     * currentLoadedClass(). Answers true if the receiver is a system class
     * loader.
     * 
     * Note that this method has package visibility only. It is defined here to
     * avoid the security manager check in getSystemClassLoader, which would be
     * required to implement this method anywhere else.
     *
     * 
     * @return <code>true</code> if the receiver is a system class loader
     * @see Class#getClassLoaderImpl()
     */
    final boolean isSystemClassLoader() {
        return BootstrapClassLoader.getBootstrapClassLoader() == this;
    }

    /**
     * <p>
     * Answers true if the receiver is the same or ancestor of another class loader.
     * </p>
     * <p>
     * Note that this method has package visibility only. It is defined here to
     * avoid the security manager check in getParent, which would be required to
     * implement this method anywhere else.
     * </p>
     * 
     * @param child
     *            A child candidate
     * @return <code>true</code> if the receiver is ancestor of the parameter
     */
    final boolean isSameOrAncestor(ClassLoader child) {
        while (child != null) {
            if (this == child) {
                return true;
            }
            child = child.parentClassLoader;
        }
        return false;
    }

    /**
     * Answers an URL which can be used to access the resource described by
     * resName, using the class loader's resource lookup algorithm. The default
     * behavior is just to return null. This should be implemented by a
     * ClassLoader.
     * 
     * @return The location of the resource.
     * @param resName
     *            The name of the resource to find.
     */
    protected URL findResource(String resName) {
        return null;
    }

    /**
     * Answers an Enumeration of URL which can be used to access the resources
     * described by resName, using the class loader's resource lookup algorithm.
     * The default behavior is just to return an empty Enumeration.
     * 
     * @param resName
     *            The name of the resource to find.
     * 
     * @return The locations of the resources.
     * 
     * @throws IOException
     *             when an error occurs
     */
    protected Enumeration<URL> findResources(String resName) throws IOException {
	// TODO: create a more efficient empty enum enumeration
	return new Vector<URL>(0).elements();
    }

    /**
     * Answers the absolute path of the file containing the library associated
     * with the given name, or null. If null is answered, the system searches
     * the directories specified by the system property "java.library.path".
     * 
     * @return The library file name or null.
     * @param libName
     *            The name of the library to find.
     */
    protected String findLibrary(String libName) {
        return null;
    }

    /**
     * Attempt to locate the requested package. If no package information can be
     * located, null is returned.
     * 
     * @param name
     *            The name of the package to find
     * @return The package requested, or null
     */
    protected Package getPackage(String name) {
        Package pkg = null;
        if (name == null) {
            throw new NullPointerException();
        }
        synchronized (definedPackages) {
            pkg = definedPackages.get(name);
        }
        if (pkg == null) {
            if (parentClassLoader == null) {
		if (this != BootstrapClassLoader.getBootstrapClassLoader()) {
		    pkg = BootstrapClassLoader.getBootstrapClassLoader().getPackage(name);
                } else {
                    pkg = null;
		}
            } else {
                pkg = parentClassLoader.getPackage(name);
            }
        }
        return pkg;
    }

    /**
     * Return all the packages known to this class loader.
     * 
     * @return All the packages known to this classloader
     */
    protected Package[] getPackages() {
        ArrayList<Package> packages = new ArrayList<Package>();
        fillPackages(packages);
        return packages.toArray(new Package[packages.size()]);
    }

    /**
     * Define a new Package using the specified information.
     * 
     * @param name
     *            The name of the package
     * @param specTitle
     *            The title of the specification for the Package
     * @param specVersion
     *            The version of the specification for the Package
     * @param specVendor
     *            The vendor of the specification for the Package
     * @param implTitle
     *            The implementation title of the Package
     * @param implVersion
     *            The implementation version of the Package
     * @param implVendor
     *            The specification vendor of the Package
     * @param sealBase
     *            If sealBase is null, the package is left unsealed. Otherwise,
     *            the the package is sealed using this URL.
     * @return The Package created
     * @throws IllegalArgumentException
     *             if the Package already exists
     */
    protected Package definePackage(String name, String specTitle,
				    String specVersion, String specVendor, String implTitle,
				    String implVersion, String implVendor, URL sealBase)
	throws IllegalArgumentException {
        synchronized (definedPackages) {
            if (getPackage(name) != null) {
                throw new IllegalArgumentException("Package " + name
						   + "has been already defined.");
            }
            Package pkg = new Package(this, name, specTitle, specVersion, specVendor,
				      implTitle, implVersion, implVendor, sealBase);
            definedPackages.put(name, pkg);
            return pkg;
        }
    }

    /**
     * Gets the signers of a class.
     * 
     * @param c
     *            The Class object
     * @return signers The signers for the class
     */
    final Object[] getSigners(Class<?> c) {
	VM.sysWriteln("TODO ClassLoader.getSigners");
        return null;
    }

    /**
     * Sets the signers of a class.
     * 
     * @param c
     *            The Class object
     * @param signers
     *            The signers for the class
     */
    protected final void setSigners(Class<?> c, Object[] signers) {
        VM.sysWriteln("TODO ClassLoader.setSigners");
        return;
    }

    /**
     * <p>
     * This must be provided by the VM vendor. It is used by
     * SecurityManager.checkMemberAccess() with depth = 3. Note that
     * checkMemberAccess() assumes the following stack when called:<br>
     * </p>
     * 
     * <pre>
     *    		&lt; user code &amp;gt; &lt;- want this class
     *    		Class.getDeclared*();
     *    		Class.checkMemberAccess();
     *    		SecurityManager.checkMemberAccess(); &lt;- current frame
     * </pre>
     * 
     * <p>
     * Returns the ClassLoader of the method (including natives) at the
     * specified depth on the stack of the calling thread. Frames representing
     * the VM implementation of java.lang.reflect are not included in the list.
     * </p>
     * Notes:
     * <ul>
     * <li>This method operates on the defining classes of methods on stack.
     * NOT the classes of receivers.</li>
     * <li>The item at depth zero is the caller of this method</li>
     * </ul>
     * 
     * @param depth
     *            the stack depth of the requested ClassLoader
     * @return the ClassLoader at the specified depth
     */
    static final ClassLoader getStackClassLoader(int depth) {
        return RVMClass.getClassLoaderFromStackFrame(depth);
    }

    /**
     * This method must be included, as it is used by System.load(),
     * System.loadLibrary(). The reference implementation of this method uses
     * the getStackClassLoader() method. Returns the ClassLoader of the method
     * that called the caller. i.e. A.x() calls B.y() calls callerClassLoader(),
     * A's ClassLoader will be returned. Returns null for the bootstrap
     * ClassLoader.
     * 
     * @return a ClassLoader or null for the bootstrap ClassLoader
     */
    static ClassLoader callerClassLoader() {
	if (org.jikesrvm.VM.runningVM) {
	    ClassLoader ans = RVMClass.getClassLoaderFromStackFrame(1);
	    if (ans == BootstrapClassLoader.getBootstrapClassLoader()) {
		return null;
	    } else {
		return ans;
	    }
	}
	else {
	    return null;
	}
    }

    /**
     * This method must be provided by the VM vendor, as it is called by
     * java.lang.System.loadLibrary(). System.loadLibrary() cannot call
     * Runtime.loadLibrary() because this method loads the library using the
     * ClassLoader of the calling method. Loads and links the library specified
     * by the argument.
     * 
     * @param libName
     *            the name of the library to load
     * @param loader
     *            the classloader in which to load the library
     * @throws UnsatisfiedLinkError
     *             if the library could not be loaded
     * @throws SecurityException
     *             if the library was not allowed to be loaded
     */
    static void loadLibraryWithClassLoader(String libName, ClassLoader loader) {
        SecurityManager sc = System.getSecurityManager();
        if (sc != null) {
	    sc.checkLink(libName);
        }
        if (loader != null) {
	    String fullLibName = loader.findLibrary(libName);
	    if (fullLibName != null) {
		loadLibrary(fullLibName, loader, null);
		return;
	    }
        }       
        String path = System.getProperty("java.library.path", "");
        path += System.getProperty("vm.boot.library.path", "");
        loadLibrary(libName, loader, path);
    }

    static final void loadLibrary (String libName, ClassLoader loader, String libraryPath) {
        SecurityManager sc = System.getSecurityManager();
        if (sc != null) {
	    sc.checkLink(libName);
        }
        String pathSeparator = System.getProperty("path.separator");
        String fileSeparator = System.getProperty("file.separator");
        if (DEBUG) {
          org.jikesrvm.VM.sysWriteln("path.separator=", pathSeparator);
          org.jikesrvm.VM.sysWriteln("file.separator=", fileSeparator);
          org.jikesrvm.VM.sysWriteln("libraryPath=", libraryPath);
        }
        String st[] = fracture(libraryPath, pathSeparator);
        int l = st.length;
        for (int i = 0; i < l; i++) {
	    // TODO: support loader argument
	    if (DEBUG) org.jikesrvm.VM.sysWriteln("load(" + st[i]+ fileSeparator + libName + ")");
	    if (DynamicLibrary.load(st[i] + fileSeparator + libName) != 0) {
		return;
	    }
        }
	org.jikesrvm.VM.sysWriteln("ULE(" + libName + ")");
        throw new UnsatisfiedLinkError(libName);
    }
  
    /**
     * This method must be provided by the VM vendor, as it is called by
     * java.lang.System.load(). System.load() cannot call Runtime.load() because
     * the library is loaded using the ClassLoader of the calling method. Loads
     * and links the library specified by the argument. No security check is
     * done.
     * 
     * @param libName
     *            the name of the library to load
     * @param loader
     *            the classloader in which to load the library
     * @param libraryPath
     *            the library path to search, or null
     * @throws UnsatisfiedLinkError
     *             if the library could not be loaded
     */
    static void loadLibraryWithPath(String libName, ClassLoader loader,
				    String libraryPath) {
	throw new Error("TODO - no reference DRLVM code");
    }

    /**
     * Sets the assertion status of a class.
     * 
     * @param cname
     *            Class name
     * @param enable
     *            Enable or disable assertion
     */
    public void setClassAssertionStatus(String cname, boolean enable) {
        VM.sysWriteln("TODO ClassLoader.setClassAssertionStatus");
        return;
    }

    /**
     * Sets the assertion status of a package.
     * 
     * @param pname
     *            Package name
     * @param enable
     *            Enable or disable assertion
     */
    public void setPackageAssertionStatus(String pname, boolean enable) {
        VM.sysWriteln("TODO ClassLoader.setPackageAssertionStatus");
        return;
    }

    /**
     * Sets the default assertion status of a classloader
     * 
     * @param enable
     *            Enable or disable assertion
     */
    public void setDefaultAssertionStatus(boolean enable) {
        VM.sysWriteln("TODO ClassLoader.setDefaultAssertionStatus");
        return;
    }

    /**
     * Clears the default, package and class assertion status of a classloader
     * 
     */
    public void clearAssertionStatus() {
	VM.sysWriteln("TODO ClassLoader.clearAssertionStatus");
        return;
    }

    /**
     * Answers the assertion status of the named class Returns the assertion
     * status of the class or nested class if it has been set. Otherwise returns
     * the assertion status of its package or superpackage if that has been set.
     * Otherwise returns the default assertion status. Returns 1 for enabled and
     * 0 for disabled.
     * 
     * @return the assertion status.
     * @param cname
     *            the name of class.
     */
    boolean getClassAssertionStatus(String cname) {
	VM.sysWriteln("TODO ClassLoader.getClassAssertionStatus");
        return false;
    }

    /**
     * Answers the assertion status of the named package Returns the assertion
     * status of the named package or superpackage if that has been set.
     * Otherwise returns the default assertion status. Returns 1 for enabled and
     * 0 for disabled.
     * 
     * @return the assertion status.
     * @param pname
     *            the name of package.
     */
    boolean getPackageAssertionStatus(String pname) {
	VM.sysWriteln("TODO ClassLoader.setPackageAssertionStatus");
        return false;
    }

    /**
     * Answers the default assertion status
     * 
     * @return boolean the default assertion status.
     */
    boolean getDefaultAssertionStatus() {
	VM.sysWriteln("TODO ClassLoader.getDefaultAssertionStatus");
        return false;
    }

    /**
     * Helper method to avoid StringTokenizer using.
     */
    private static String[] fracture(String str, String sep) {
        if (str.length() == 0) {
            return new String[0];
        }
        ArrayList<String> res = new ArrayList<String>();
        int in = 0;
        int curPos = 0;
        int i = str.indexOf(sep);
        int len = sep.length();
        while (i != -1) {
            String s = str.substring(curPos, i); 
            res.add(s);
            in++;
            curPos = i + len;
            i = str.indexOf(sep, curPos);
        }

        len = str.length();
        if (curPos <= len) {
            String s = str.substring(curPos, len); 
            in++;
            res.add(s);
        }

        return res.toArray(new String[in]);
    }


    /**
     * Helper method for defineClass(...)
     * 
     * @return null if the package already has the same set of certificates, if
     *         first class in the package is being defined then array of
     *         certificates extracted from codeSource is returned.
     * @throws SecurityException if the package has different set of
     *         certificates than codeSource
     */
    private Certificate[] getCertificates(String packageName,
                                          CodeSource codeSource) {
        Certificate[] definedCerts = packageCertificates
            .get(packageName);
        Certificate[] classCerts = codeSource != null
            ? codeSource.getCertificates() : EMPTY_CERTIFICATES;
        classCerts = classCerts != null ? classCerts : EMPTY_CERTIFICATES;
        // not first class in the package
        if (definedCerts != null) {
            if (!compareAsSet(definedCerts, classCerts)) {
                throw new SecurityException("It is prohobited to define a "
					    + "class which has different set of signers than "
					    + "other classes in this package");
            }
            return null;
        }
        return classCerts;
    }

    /**
     * Helper method for the getPackages() method.
     */
    private void fillPackages(ArrayList<Package> packages) {
        if (parentClassLoader != null) {
            parentClassLoader.fillPackages(packages);
        }
        synchronized (definedPackages) {
            packages.addAll(definedPackages.values());
        }
    }

    /**
     * Neither certs1 nor certs2 cann't be equal to null.
     */
    private boolean compareAsSet(Certificate[] certs1, Certificate[] certs2) {
        // TODO Is it possible to have multiple instances of same
        // certificate in array? This implementation assumes that it is
        // not possible.
        if (certs1.length != certs1.length) {
            return false;
        }
        if (certs1.length == 0) {
            return true;
        }
        boolean[] hasEqual = new boolean[certs1.length];
        for (int i = 0; i < certs1.length; i++) {
            boolean isMatch = false;
            for (int j = 0; j < certs2.length; j++) {
                if (!hasEqual[j] && certs1[i].equals(certs2[j])) {
                    hasEqual[j] = isMatch = true;
                    break;
                }
            }
            if (!isMatch) {
                return false;
            }
        }
        return true;
    }
}
