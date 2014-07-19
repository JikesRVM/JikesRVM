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
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <xsl:template match="report">

    <p>Copy these results into rvm/src/org/jikesrvm/adaptive/recompilation/CompilerDNA.java</p>
    <pre>
    <xsl:variable name="baselineCompilationRate" select="build-target/build-configuration/test-configuration/group/test/test-execution/statistics/statistic[../../../../../name[text()='Measure_Compilation_Base'] and @key='Base.bcb/ms']/@value"/>
    <xsl:variable name="opt0CompilationRate" select="build-target/build-configuration/test-configuration/group/test/test-execution/statistics/statistic[../../../../../name[text()='Measure_Compilation_Opt_0'] and @key='Opt.bcb/ms']/@value"/>
    <xsl:variable name="opt1CompilationRate" select="build-target/build-configuration/test-configuration/group/test/test-execution/statistics/statistic[../../../../../name[text()='Measure_Compilation_Opt_1'] and @key='Opt.bcb/ms']/@value"/>
    <xsl:variable name="opt2CompilationRate" select="build-target/build-configuration/test-configuration/group/test/test-execution/statistics/statistic[../../../../../name[text()='Measure_Compilation_Opt_2'] and @key='Opt.bcb/ms']/@value"/>

    <xsl:variable name="baselineAggregateBestScore" select="build-target/build-configuration/test-configuration/group/test/test-execution/statistics/statistic[../../../../../name[text()='Measure_Performance_Base'] and @key='aggregate.best.score']/@value"/>
    <xsl:variable name="opt0AggregateBestScore" select="build-target/build-configuration/test-configuration/group/test/test-execution/statistics/statistic[../../../../../name[text()='Measure_Performance_Opt_0'] and @key='aggregate.best.score']/@value"/>
    <xsl:variable name="opt1AggregateBestScore" select="build-target/build-configuration/test-configuration/group/test/test-execution/statistics/statistic[../../../../../name[text()='Measure_Performance_Opt_1'] and @key='aggregate.best.score']/@value"/>
    <xsl:variable name="opt2AggregateBestScore" select="build-target/build-configuration/test-configuration/group/test/test-execution/statistics/statistic[../../../../../name[text()='Measure_Performance_Opt_2'] and @key='aggregate.best.score']/@value"/>

    compilationRates = new double[]{<xsl:value-of select="$baselineCompilationRate"/>,
                                    <xsl:value-of select="$opt0CompilationRate"/>,
                                    <xsl:value-of select="$opt1CompilationRate"/>,
                                    <xsl:value-of select="$opt2CompilationRate"/>};
    speedupRates = new double[]{1,
                                (<xsl:value-of select="$opt0AggregateBestScore"/>/<xsl:value-of select="$baselineAggregateBestScore"/>),
                                (<xsl:value-of select="$opt1AggregateBestScore"/>/<xsl:value-of select="$baselineAggregateBestScore"/>),
                                (<xsl:value-of select="$opt2AggregateBestScore"/>/<xsl:value-of select="$baselineAggregateBestScore"/>) };
    </pre>
  </xsl:template>
</xsl:stylesheet>
