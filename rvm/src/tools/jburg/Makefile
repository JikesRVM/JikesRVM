#
# (C) Copyright IBM Corp. 2001
#
#$Id$

include		$(RVM_ROOT)/rvm/build.Make.rules

YFLAGS=

tool:
	@$(MAKE) $(SCRATCH_DIR)/jburg

$(SCRATCH_DIR)/jburg:	jburg.c gram.y jburg.h
	$(CP) jburg.c $(SCRATCH_DIR)
	$(CP) jburg.h $(SCRATCH_DIR)
	$(CP) gram.y  $(SCRATCH_DIR)
	(cd $(SCRATCH_DIR) && (export TMPDIR=.; $(MAKE) gram.c; $(CC) -o jburg jburg.c gram.c))

.PHONY:	tool


