#
# (C) Copyright IBM Corp. 2001
#
#$Id$

# @author Bowen Alpern
# @author Derek Lieber

include			$(RVM_ROOT)/rvm/build.Make.rules

MakeTemplates:		$(GEN_FILES_DIR)/VM_TypeReferenceVector.java \
			$(GEN_FILES_DIR)/VM_FieldVector.java \
			$(GEN_FILES_DIR)/VM_MethodVector.java
			@print -n "(classloader templates expanded) "

$(GEN_FILES_DIR)/VM_TypeReferenceVector.java:	VM_Vector.template
		./InstantiateVectorTemplate com.ibm.JikesRVM.classloader VM_TypeReferenceVector VM_TypeReference VM_Vector.template $(GEN_FILES_DIR)/VM_TypeReferenceVector.java

$(GEN_FILES_DIR)/VM_FieldVector.java:	VM_Vector.template
		./InstantiateVectorTemplate com.ibm.JikesRVM.classloader VM_FieldVector VM_Field VM_Vector.template $(GEN_FILES_DIR)/VM_FieldVector.java

$(GEN_FILES_DIR)/VM_MethodVector.java:	VM_Vector.template
		./InstantiateVectorTemplate com.ibm.JikesRVM.classloader VM_MethodVector VM_Method VM_Vector.template $(GEN_FILES_DIR)/VM_MethodVector.java
