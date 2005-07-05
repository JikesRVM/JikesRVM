#! /usr/bin/env bash
#
# (C) Copyright IBM Corp. 2001, 2003
#
#$Id$
#
# @author Julian Dolby
# @modified Steven Augart

## This file is named "jdoc.sh" for historical reasons.  It actually runs in
## Bash, so that we can get pretty formatting under AIX by using "echo -n".

unset DEBUG
ME="${0##*/}"

croak () {
    msg="$*"
    echo >&2 "$ME: ${msg:-Something bad happened.}  Aborting execution."
    exit 1
}

usage() {
    echo "Usage: $ME <destination-dir> [ -leave-fields | -leave-classes | -leave-methods ]"
}


SUN_LINK=http://java.sun.com/j2se/1.4/docs/api

# make sure we have a repository to use
if [[ ! $RVM_ROOT ]]; then
    echo Please set your RVM_ROOT variable
    exit 31
fi

# should tables of inherited stuff be stripped?
STRIP_FIELDS=1			# inherited fields
STRIP_CLASSES=1			# inherited inner classes
STRIP_METHODS=1			# inherited methods

unset DEST_DIR
while (( $# > 0 )); do
    arg="$1"
    if [[ $arg == --* ]]; then
	arg=${arg#"-"};
    fi

    case "$1" in
	-leave-fields )
	    STRIP_FIELDS=0; shift; ;;
	-leave-classes )
	    STRIP_CLASSES=0; shift; ;;
	-leave-methods )
	    STRIP_METHODS=0; shift ;;
	-* )
	    croak "Got the unknown option \"$1\"" ; shift ; ;;
	* )
	    if [[ $DEST_DIR ]]; then
		usage >&2 ;
		croak "\
Got multiple DEST_DIR specifications on the command line:
      \"$DEST_DIR\" was followed by \"$1\"." ;
	    else
		declare -r DEST_DIR="$1"
		shift;
	    fi
	    ;;
    esac
done
if [[ ! $DEST_DIR ]]; then
    usage >&2;
    croak "You must specify a <destination-dir>"
fi


# make build directory
mkdir -p $DEST_DIR || croak "Unable to create the destination, $DEST_DIR";
export RVM_BUILD="$DEST_DIR/tmp_build"
echo "$ME: Setting up a development build in $RVM_BUILD"
echo "$ME:      based on the source code in $RVM_ROOT"
if [[ -f $RVM_BUILD/RVM.classes/Dummy.class ]]; then
    echo "$ME: $RVM_BUILD seems to be already built;
	 we will just use it."
else
    $RVM_ROOT/rvm/bin/jconfigure development < /dev/null || croak "jconfigure failed."
    cd $RVM_BUILD || croak "Can't get to $RVM_BUILD"
    ./jbuild -nolink -nobooter || croak "\"./jbuild -nolink -nobooter\" in $RVM_BUILD failed."
fi

# status message
echo -n "$ME: "

# pick up env
. $RVM_BUILD/environment.host || croak "Cannot load $RVM_BUILD/ennvironment.host"

# be in dir such that the pathnames which find will produce will
# match package names
cd $RVM_BUILD/RVM.classes || croak "Unable to change directory to $RVM_BUILD/RVM.classes; something is badly broken."

# extract MMTk from its jar so that we'll include javadoc for it as well.
${HOST_JAR} xf mmtk.jar

# Only generate javadoc for files that are actually really in this build
for f in $($FIND . -name \*.java); do
    if [[ ! -e ${f%.java}.class ]]; then
        ## delete anything that wasn't compiled for this build.
        rm $f
    fi
done
# ignore these files; we don't want them in the javadoc
if [[ -e Dummy.java ]]; then
    rm Dummy.java OptDummy.java
fi
echo -n "(sources processed) "
    

# collect the JikesRVM packages; for these packages we want all files, so
# we will just use the package name.
PACKAGES=$(
    # Here, we skip the java.* packages; just get our own.
    for _d in $($FIND . -type d -a ! -path './java*'); do
	if [ $($FIND $_d -type f -maxdepth 1 -name '*.java' | wc -l) != 0 ]; then
	    echo $_d
	    # Turn a path of the form .//com/ibm/jikesrvm (for example) into
	    # a package name, of the form: com.ibm.jikesrvm

	    # Turn slashes into dots.
	    # Get rid of the leading dots in the package names.
	    # Nuke the nonexistent package named by the empty string.
        fi | $SED -e 's@/@.@g' -e 's/^\.*//' -e '/^$/d'
    done
);

#run javadoc
rm -f $DEST_DIR/javadoc.out

# xargs -t: means be verbose; print out the cmd. line before executing it.
# NB: do NOT quote $PACKAGES in the following:
## We use -breakiterator to be forward-compatible.
$FIND . -name '*.java' -maxdepth 1 -type f | xargs -t ${HOST_JAVADOC} -breakiterator -tag date:a:"Last (significant) modification:" -tag author:a:"Author:" -tag modified:a:"Modified by:" -J-Xmx200M -link $SUN_LINK -private -author -classpath $RVM_BUILD/RVM.classes/:$RVM_BUILD/RVM.classes/rvmrt.jar -d $DEST_DIR $PACKAGES >> $DEST_DIR/javadoc.out 2>&1

echo -n "(javadoc complete) "

# no more need for build dir
# AIX won't let us rm the directory while we are sill in it...
cd $DEST_DIR

# post-process if desired
cd $DEST_DIR

# OS=$(uname)
# function new_tmp_fname  () {
#     if [[ $OS = Linux ]]; then
# 	mktemp /tmp/strip.XXXXXX
#     else
# 	echo ./xxx
#     fi
# }

function clean_table_named () {
    local table_name="$1"
    echo -n "(cleaning $table_name..."
    for f in $($FIND . -name '*.html'); do
	TMP="${f}.tmp"

	$AWK '
	    BEGIN { 
		    discard_table = 0; find_table = 0; 
	    }

	    /<A NAME="'$table_name'_inherited_from_class/ { 
		    find_table = 1; 
	    }

	    find_table==1 && /<TABLE/ { 
		    discard_table = 1; 
	    }

	    discard_table==0 { 
		    print $0; 
	    }

	    discard_table==1 {
		    # Do nothing.
	    }

	    discard_table==1 && /<\/TABLE/ { 
		    find_table = 0; 
		    discard_table = 0; 
	    }

	      ' $f >| $TMP
	mv -f $TMP $f
    done
    echo -n "cleaned) "
}

if (( STRIP_FIELDS == 1 )); then
    clean_table_named "fields";
fi

if (( STRIP_METHODS == 1 )); then
    clean_table_named "methods"
fi

if (( STRIP_CLASSES == 1 )); then
    clean_table_named "inner_classes"
fi

if (( STRIP_CLASSES  ==  1 )) \
	||  (( STRIP_FIELDS == 1 )) \
	|| (( STRIP_METHODS == 1 ))
then
    echo -n "(postprocessing done) "
else
    echo -n "(no postprocessing necessary) "
fi

echo
