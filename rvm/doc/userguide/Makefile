config_file := $(shell . $(RVM_HOST_CONFIG) && echo $$USER_GUIDE_CONFIG)

HOST_JAVA := $(shell . $(RVM_HOST_CONFIG) && echo $$HOST_JAVA)
HOST_JAVAC := $(shell . $(RVM_HOST_CONFIG) && echo $$HOST_JAVAC)
HOST_REPOSITORIES := $(shell . $(RVM_HOST_CONFIG) && echo $$HOST_REPOSITORIES)

GENERATOR_DIR := $(RVM_ROOT)/rvm/src/tools/templateExpander
GENERATOR := GenerateFromTemplate

OPTION_DIR := $(RVM_ROOT)/rvm/src/vm/compilers/optimizing/driver
SHARED_DIR := $(RVM_ROOT)/rvm/src/vm/compilers/baseline
OPT_BOOLEAN_DAT_FILES := ${SHARED_DIR}/SharedBooleanOptions.dat ${OPTION_DIR}/BooleanOptions.dat
OPT_VALUE_DAT_FILES := ${SHARED_DIR}/SharedValueOptions.dat ${OPTION_DIR}/ValueOptions.dat
OPT_DAT_FILES := $(OPT_BOOLEAN_DAT_FILES) $(OPT_VALUE_DAT_FILES)

ADAPT_OPTION_DIR := $(RVM_ROOT)/rvm/src/vm/adaptive/utility/
ADAPT_BOOLEAN_DAT_FILES := ${ADAPT_OPTION_DIR}/BooleanOptions.dat \
			   ${ADAPT_OPTION_DIR}/SharedBooleanOptions.dat
ADAPT_VALUE_DAT_FILES := ${ADAPT_OPTION_DIR}/ValueOptions.dat \
			 ${ADAPT_OPTION_DIR}/SharedValueOptions.dat
ADAPT_DAT_FILES := $(ADAPT_BOOLEAN_DAT_FILES) $(ADAPT_VALUE_DAT_FILES)

include $(config_file)
LATEX  ?= latex
DVIPS ?= dvips
BIBTEX ?= bibtex
MAKEINDEX ?= makeindex
HYPERLATEX ?= hyperlatex
PS2GIF ?= ps2gif

INPUT_FILES = 	userguide.tex \
		ack.tex \
		cmdline.tex \
		codingstyle.tex \
		faq.tex \
		installation.tex \
		intro.tex \
		jdp.tex \
		mmdetails.tex \
		optdetails.tex \
		performance.tex \
		profiling.tex \
		regression.tex \
		running.tex \
		remark.tex \
		vmdetails.tex

GEN_INPUTS =	opt_options.tex adaptive_options.tex

GENERATOR_FILES = ${GENERATOR}.class QuotedStringTokenizer.class \
		  UnterminatedStringException.class

TEX_FILES = $(INPUT_FILES) $(GEN_INPUTS) 

TEX_GEN_FILES = userguide.aux userguide.bbl userguide.blg userguide.dvi userguide.log userguide.lof userguide.toc userguide.idx userguide.ilg userguide.ind adaptive_options.aux opt_options.aux

HTML_GRAPHICS = object.gif jtoc.gif stack.gif arch-AOSOverview.gif

# do not add more white spaces to the following line
NAV_GIFS=blank.gif next.gif previous.gif up.gif

HTML_DIR = HTML


# by default make the PostScript version
ps:		userguide.ps
userguide.ps:	userguide.dvi
		$(DVIPS) userguide -o

# make the PostScript and the HTML version
all:	userguide.ps html

# TeX the document
userguide.dvi:	$(TEX_FILES) main.bib
		$(LATEX) userguide
		$(BIBTEX) userguide
		$(MAKEINDEX) userguide
		$(LATEX) userguide
		$(LATEX) userguide

html:	userguide.tex $(TEX_FILES) $(HTML_GRAPHICS)
	rm -rf $(HTML_DIR)
	$(HYPERLATEX) userguide
	cp ${NAV_GIFS} ${HTML_GRAPHICS} ${HTML_DIR}
	cd ${HTML_DIR} && ln -sf userguide.html index.html
	chmod -Rf u+rwX ${HTML_DIR}
	chmod -Rf g+rwX ${HTML_DIR}


%.gif:	%.ps
	${PS2GIF} -res 50 $< $@

.INTERMEDIATE:	${GENERATOR}.java

clean:;	rm -f 	${TEX_GEN_FILES} $(GEN_INPUTS) ${GENERATOR_FILES} userguide.dvi

dist-clean:	clean
		rm -f userguide.ps
		rm -rf ${HTML_DIR}

distrib:;	mkdir -p $(distdir)/ps
		-rm -f $(distdir)/ps/userguide.ps
		-rm -rf $(distdir)/HTML
		-cp userguide.ps $(distdir)/ps
		-cp -r ${HTML_DIR} $(distdir)
		-chmod -Rf g+rwX ${distdir}
		-chmod -Rf o+rX ${distdir}



${GENERATOR}.java:	${GENERATOR_DIR}/${GENERATOR}.java
			cp $< $@

${GENERATOR_FILES}:	${GENERATOR}.java
			$(HOST_JAVAC) -g -d . $<

opt_options.tex:	${GENERATOR_FILES} options.template ${OPT_DAT_FILES}
			$(HOST_JAVA) ${GENERATOR} options.template \
			  opt_options.tex \
			  BOOLEAN_DAT_FILES="$(OPT_BOOLEAN_DAT_FILES)" \
			  VALUE_DAT_FILES="$(OPT_VALUE_DAT_FILES)" \
			  OPTPREFIX="-X:irc" \
			  USE_OPT_LEVELS="YES"



adaptive_options.tex:	${GENERATOR_FILES} options.template $(ADAPT_DAT_FILES)
			$(HOST_JAVA) ${GENERATOR} options.template \
			  adaptive_options.tex \
			  BOOLEAN_DAT_FILES="$(ADAPT_BOOLEAN_DAT_FILES)" \
			  VALUE_DAT_FILES="$(ADAPT_VALUE_DAT_FILES)" \
			  OPTPREFIX="-X:aos" \
			  USE_OPT_LEVELS="NO"
