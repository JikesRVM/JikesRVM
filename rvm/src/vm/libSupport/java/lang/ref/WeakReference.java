package java.lang.ref;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;

/**
 * Implementation of java.lang.ref.WeakReference for JikesRVM.
 * @author Chris Hoffmann
 * @see java.util.WeakHashtable 
 */
public class WeakReference extends Reference {

  public WeakReference(Object referent) {
    super(referent);
    MM_Interface.addWeakReference(this);
  }

  public WeakReference(Object referent, ReferenceQueue q) {
    super(referent, q);
    MM_Interface.addWeakReference(this);
  }

}
