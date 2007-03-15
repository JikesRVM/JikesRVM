<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <xsl:variable name="target" select="/report/target/parameters/parameter[@key='target.name']/@value"/>

  <xsl:template match="report">
    <html>
      <head>
        <title>Tests Results for <xsl:value-of select="id"/> on <xsl:value-of select="$target"/></title>
        <style>
          body {
          margin-left: 10;
          margin-right: 10;
          font:normal 80% arial,helvetica,sanserif;
          background-color:#FFFFFF;
          color:#000000;
          }
          table.errors { width: 80%; }
          tr.oddrow td { background: #efefef; }
          tr.evenrow td { background: #fff; }
          th, td { text-align: left; vertical-align: top; }
          th { font-weight:bold; background: #ccc; color: black; }
          table, th, td { font-size:100%; border: none }
          h2 { font-weight:bold; font-size:140%; margin-bottom: 5; }
        </style>
      </head>
      <body>
        <xsl:variable name="total-tests" select="count(/report/configuration/test-configuration/test-group/test)"/>
        <xsl:variable name="total-successes" select="count(/report/configuration/test-configuration/test-group/test/result[text()='SUCCESS'])"/>
        <xsl:variable name="total-excluded" select="count(/report/configuration/test-configuration/test-group/test/result[text()='EXCLUDED'])"/>

        <h2>Total Success Rate <xsl:value-of select="$total-successes"/>/<xsl:value-of select="$total-tests"/> (<xsl:value-of select="$total-excluded"/> excluded)</h2>
        <p>Subversion Revision: <xsl:value-of select="revision"/></p>
        <xsl:if test="/report/configuration/id[text()='production']/../test-configuration/test-group/id[text()='SPECjvm98']/../test/result[text()='SUCCESS']/../statistics">
          <h3>SPECjvm98 Performance</h3>
          <p>Aggregate Score: <xsl:value-of select="/report/configuration/id[text()='production']/../test-configuration/test-group/id[text()='SPECjvm98']/../test/result[text()='SUCCESS']/../statistics/statistic[@key='aggregate.best.score']/@value"/></p>
          <table class="performance">
            <tr>
              <th>Name</th>
              <th>Best Time</th>
              <th>Best Ratio</th>
              <th>First Time</th>
              <th>First Ratio</th>
            </tr>
            <xsl:call-template name="spec98-row">
              <xsl:with-param name="name" select="'compress'"/>
            </xsl:call-template>
            <xsl:call-template name="spec98-row">
              <xsl:with-param name="name" select="'jess'"/>
            </xsl:call-template>
            <xsl:call-template name="spec98-row">
              <xsl:with-param name="name" select="'db'"/>
            </xsl:call-template>
            <xsl:call-template name="spec98-row">
              <xsl:with-param name="name" select="'javac'"/>
            </xsl:call-template>
            <xsl:call-template name="spec98-row">
              <xsl:with-param name="name" select="'mpegaudio'"/>
            </xsl:call-template>
            <xsl:call-template name="spec98-row">
              <xsl:with-param name="name" select="'mtrt'"/>
            </xsl:call-template>
            <xsl:call-template name="spec98-row">
              <xsl:with-param name="name" select="'jack'"/>
            </xsl:call-template>
          </table>
        </xsl:if>
        <xsl:if test="($total-tests - $total-successes - $total-excluded) != 0">
        <table class="errors">
          <tr>
            <th>Result</th>
            <th>Configuration</th>
            <th>Run</th>
            <th>Suite</th>
            <th>Test</th>
            <th>Reason</th>
          </tr>
          <xsl:apply-templates select="/report/configuration/test-configuration/test-group/test/result[not(text()='SUCCESS' or text()='EXCLUDED')]"/>
        </table>
        </xsl:if>
      </body>
    </html>
  </xsl:template>

  <xsl:template match="test/result">
    <tr>
      <xsl:call-template name="alternated-row"/>
      <td><xsl:value-of select="."/></td>
      <td><xsl:value-of select="../../../../id"/></td>
      <td><xsl:value-of select="../../../id"/></td>
      <td><xsl:value-of select="../../id"/></td>
      <td><xsl:value-of select="../id"/></td>
      <td><xsl:value-of select="../result-explanation"/></td>
    </tr>
  </xsl:template>

  <xsl:template name="spec98-row">
    <xsl:param name="name"/>
    <tr>
      <td><xsl:value-of select="$name"/></td>
      <td><xsl:value-of select="/report/configuration/id[text()='production']/../test-configuration/test-group/id[text()='SPECjvm98']/../test/result[text()='SUCCESS']/../statistics/statistic[@key=concat($name,'.best.time')]/@value"/></td>
      <td><xsl:value-of select="/report/configuration/id[text()='production']/../test-configuration/test-group/id[text()='SPECjvm98']/../test/result[text()='SUCCESS']/../statistics/statistic[@key=concat($name,'.best.ratio')]/@value"/></td>
      <td><xsl:value-of select="/report/configuration/id[text()='production']/../test-configuration/test-group/id[text()='SPECjvm98']/../test/result[text()='SUCCESS']/../statistics/statistic[@key=concat($name,'.first.time')]/@value"/></td>
      <td><xsl:value-of select="/report/configuration/id[text()='production']/../test-configuration/test-group/id[text()='SPECjvm98']/../test/result[text()='SUCCESS']/../statistics/statistic[@key=concat($name,'.first.ratio')]/@value"/></td>
    </tr>
  </xsl:template>

  <xsl:template name="alternated-row">
    <xsl:attribute name="class">
      <xsl:if test="position() mod 2 = 1">oddrow</xsl:if>
      <xsl:if test="position() mod 2 = 0">evenrow</xsl:if>
    </xsl:attribute>
  </xsl:template>
</xsl:stylesheet>