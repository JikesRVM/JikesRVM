/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Julian Dolby
 */
class OPT_CompoundSpecializationGraphEdgeEnumeration
    extends OPT_CompoundEnumerator
    implements OPT_SpecializationGraphEdgeEnumeration
{
    OPT_CompoundSpecializationGraphEdgeEnumeration(
		     OPT_SpecializationGraphEdgeEnumeration first,
		     OPT_SpecializationGraphEdgeEnumeration second)
    {
	super(first, second);
    }

    public OPT_SpecializationGraphEdge next() {
	return (OPT_SpecializationGraphEdge)nextElement();
    }
}

