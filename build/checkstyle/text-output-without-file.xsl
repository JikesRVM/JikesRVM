<?xml version="1.0"?>
<!--
 ~  This file is part of the Jikes RVM project (http://jikesrvm.org).
 ~
 ~  This file is licensed to You under the Eclipse Public License (EPL);
 ~  You may not use this file except in compliance with the License. You
 ~  may obtain a copy of the License at
 ~
 ~      http://www.opensource.org/licenses/eclipse-1.0.php
 ~
 ~  See the COPYRIGHT.txt file distributed with this work for information
 ~  regarding copyright ownership.
 -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="text"/>

<xsl:template match="checkstyle">
Checkstyle Results for Checkstyle assertion plugin test case
============================================================<xsl:for-each select="file[error]">

<xsl:for-each select="error">
 at line <xsl:value-of select="@line"/>,<xsl:value-of select="@column"/> : <xsl:value-of select="@message"/> 
</xsl:for-each>
</xsl:for-each>

Found <xsl:value-of select="count(file/error)"/> errors in <xsl:value-of select="count(file[error])"/> of <xsl:value-of select="count(file)"/> files.
</xsl:template>
</xsl:stylesheet>
