/*
 * (C) Copyright IBM Corp. 2001
 */
import java.util.NoSuchElementException;

class OPT_EmptySpecializationGraphEdgeEnumeration
    implements OPT_SpecializationGraphEdgeEnumeration 
{

    public boolean hasMoreElements() {
	return false;
    }

    public OPT_SpecializationGraphEdge next() {
	throw new NoSuchElementException();
    }

    public Object nextElement() {
	return next();
    }
}
