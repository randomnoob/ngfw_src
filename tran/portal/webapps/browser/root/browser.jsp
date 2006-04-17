<%@ page language="java" import="jcifs.smb.*" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core" %>
<%@ taglib prefix="browser" uri="WEB-INF/browser.tld" %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html xmlns="http://www.w3.org/1999/xhtml">

<head>
<title>Metavize Portal</title>
<style type="text/css">
<!--
@import url(img/dwtimgs.css);
-->
</style>
<link rel="stylesheet" type="text/css" href="common.css"/>
<link rel="stylesheet" type="text/css" href="dwt.css"/>
<link rel="stylesheet" type="text/css" href="browser.css"/>

<script type="text/javascript" src="I18nMsg.js"></script>
<script type="text/javascript" src="AjxMsg.js"></script>

<jsp:include page="Ajax.jsp"/>

<script type="text/javascript" src="CifsNode.js"></script>
<script type="text/javascript" src="Browser.js"></script>
<script type="text/javascript" src="DirTree.js"></script>
<script type="text/javascript" src="DetailPanel.js"></script>
<script type="text/javascript" src="FileUploadPanel.js"></script>
<script type="text/javascript" src="FileUploadDialog.js"></script>
<script type="text/javascript" src="MkdirDialog.js"></script>
<script type="text/javascript" src="RenameDialog.js"></script>

</head>

<body>

<noscript><p><b>Please enable JavaScript to use this application</b></p></noscript>

<script language="JavaScript">
function launch() {
DBG = new AjxDebug(AjxDebug.DBG1, null, false);

var shell = new DwtShell("MainShell", false);
new Browser(shell, "smb://windows.metavize.com/");
}
AjxCore.addOnloadListener(launch);
</script>

</body>

</html>
