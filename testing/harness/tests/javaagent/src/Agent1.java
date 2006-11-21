/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Elias Naur 2006
 * 
 * $Id$
 */
import java.lang.instrument.*;
import java.security.ProtectionDomain;

/**
 * @author Elias Naur
 */
public class Agent1 implements ClassFileTransformer {
	public static void premain(String args, Instrumentation instrumentation) {
		System.out.println("Running premain with args: " + args);
		System.out.println("Adding class transformer");
        instrumentation.addTransformer(new Agent1());
		Object[] array = new Object[10];
		Object obj = new Object();
		long array_size = instrumentation.getObjectSize(array);
		long object_size = instrumentation.getObjectSize(obj);
		// Conservatively assume sizeof(Object) > 0 and sizeof(array) >= array.length
		boolean array_size_ok = array_size >= array.length;
		boolean object_size_ok = object_size > 0;
		System.out.println("Array size ok: " + array_size_ok);
		System.out.println("Object size ok: " + object_size_ok);
		Class[] initiated_classes = instrumentation.getInitiatedClasses(Agent1.class.getClassLoader());
		boolean self_found = findClass(initiated_classes, Agent1.class);
		System.out.println("Found ourself in the initiated class list: " + self_found);
		Class[] all_classes = instrumentation.getAllLoadedClasses();
		self_found = findClass(all_classes, Agent1.class);
		System.out.println("Found ourself in the all classes list: " + self_found);
	}

	private static boolean findClass(Class[] class_list, Class klass) {
		// See if we're in the list of initiated classes
		for (int i = 0; i < class_list.length; i++)
			if (class_list[i] == klass)
				return true;
		return false;
	}

	public byte[] transform(ClassLoader loader, String className, Class classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
		System.out.println("Transforming class: " + className);
		// I'm too lazy to actually change the class, so we'll just pretend we did by returning non-null
		return classfileBuffer;
	}
}
