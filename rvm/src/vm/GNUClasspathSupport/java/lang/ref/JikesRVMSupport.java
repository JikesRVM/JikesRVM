package java.lang.ref;

public class JikesRVMSupport {

    public static void setReferenceLock(Object o) {
	Reference.lock = o;
    }

}
