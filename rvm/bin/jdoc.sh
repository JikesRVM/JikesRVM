#!/bin/sh
#
# (C) Copyright IBM Corp. 2001
#
#$Id$
#
# @author Julian Dolby

DEST_DIR=$1
shift

SUN_LINK=http://java.sun.com/j2se/1.4/docs/api

# make sure we have a repository to use
if [ x$RVM_ROOT = x ]; then
  echo Please set your RVM_ROOT variable
  exit -1
fi

# should tables of inherited stuff be stripped?
STRIP_FIELDS=1
STRIP_CLASSES=1
STRIP_METHODS=1
while [ $# != 0 ]; do
  if [ x$2 = x-leave-fields ]; then
    STRIP_FIELDS=0
  elif [ x$2 = x-leave-classes ]; then
    STRIP_CLASSES=0
  elif [ x$2 = x-leave-methods ]; then
    STRIP_METHODS=0
  fi
  shift
done


# make build directory
mkdir -p $DEST_DIR
export RVM_BUILD=$DEST_DIR/tmp_build
$RVM_ROOT/rvm/bin/jconfigure FullAdaptiveSemispace < /dev/null
cd $RVM_BUILD
./jbuild -nolink -nobooter


# status message
echo -n "`basename $0`: "

# pick up env
. $RVM_BUILD/jbuild.environment

# be in dir such that pathnames produced by find match package names
cd $RVM_BUILD/RVM.classes

# Strip out INSTRUCTION typedef and reomve all .java files that
# were not actually compiled in this build.
if [ `uname -m`==i686 ]; then
  INSTRUCTION_TYPE=byte
else
  INSTRUCTION_TYPE=int
fi

for f in `$FIND . -name \*.java`; do
  # Preprocess and preserve only files compiled for this build
  if [ -e `dirname $f`/`basename $f java`class ]; then
    # strip INSTRUCTION typedef (someday, maybe we'll write in Java :)
    $SED s/\\\<INSTRUCTION\\\>/$INSTRUCTION_TYPE/g $f > $f.tmp
    mv $f.tmp $f
  else
    rm $f
  fi
done

echo -n "(sources processed) "

# collect the JikesRVM packages; for these packages we want all files
PACKAGES=
for _d in `$FIND . -type d -a ! -path './java*'`; do
  if [ `$FIND $_d -type f -maxdepth 1 -name '*.java' | wc -l` != 0 ]; then
    PACKAGES="$PACKAGES `echo $_d | $SED s@^\./\*@@g | $SED s@/@.@g`"
  fi
done

# collect the java.* files; for these packages we want only our files
FILES=`$FIND java -name '*.java'`

#run javadoc
rm -f $DEST_DIR/javadoc.out
$FIND . -name '*.java' -maxdepth 1 -type f | xargs -t ${HOST_JAVADOC} -link $SUN_LINK -private -author -classpath $RVM_BUILD/RVM.classes/:$RVM_BUILD/RVM.classes/rvmrt.jar -d $DEST_DIR $PACKAGES $FILES >> $DEST_DIR/javadoc.out 2>&1

echo -n "(javadoc complete) "

# no more need for build dir
# AIX won't let us rm the directory while we are sill in it...
cd $DEST_DIR
rm -rf $RVM_BUILD

# post-process if desired
cd $DEST_DIR

if [ $STRIP_FIELDS -eq 1 ]; then
    for f in `$FIND . -name '*.html'`; do
      if [ `uname` = Linux ]; then
        TMP=`mktemp /tmp/strip.XXXXXX`
      else
        TMP=./xxx
      fi
      $AWK '
        BEGIN { discard_table = 0; find_table = 0; }
        /<A NAME="fields_inherited_from_class/ { find_table = 1; }
        find_table==1 && /<TABLE/ { discard_table = 1; }
        discard_table==0 { print $0; }
        discard_table==1 { }
        discard_table==1 && /<\/TABLE/ { find_table = 0; discard_table = 0; }
      ' $f > $TMP
      mv $TMP $f
    done
fi

if [ $STRIP_METHODS -eq 1 ]; then
    for f in `$FIND . -name '*.html'`; do
      if [ `uname` = Linux ]; then
        TMP=`mktemp /tmp/strip.XXXXXX`
      else
        TMP=./xxx
      fi
      $AWK '
        BEGIN { discard_table = 0; find_table = 0; }
        /<A NAME="methods_inherited_from_class/ { find_table = 1; }
        find_table==1 && /<TABLE/ { discard_table = 1; }
        discard_table==0 { print $0; }
        discard_table==1 { }
        discard_table==1 && /<\/TABLE/ { find_table = 0; discard_table = 0; }
      ' $f > $TMP
      mv $TMP $f
    done
fi

if [ $STRIP_CLASSES -eq 1 ]; then
    for f in `$FIND . -name '*.html'`; do
      if [ `uname` = Linux ]; then
        TMP=`mktemp /tmp/strip.XXXXXX`
      else
        TMP=./xxx
      fi
      $AWK '
        BEGIN { discard_table = 0; find_table = 0; }
        /<A NAME="inner_classes_inherited_from_class/ { find_table = 1; }
        find_table==1 && /<TABLE/ { discard_table = 1; }
        discard_table==0 { print $0; }
        discard_table==1 { }
        discard_table==1 && /<\/TABLE/ { find_table = 0; discard_table = 0; }
      ' $f > $TMP
      mv $TMP $f
      chmod +r $f
    done
fi

if [ $STRIP_CLASSES -eq 1 -o $STRIP_FIELDS -eq 1 ]; then
  echo -n "(postprocessing done) "
fi

echo
