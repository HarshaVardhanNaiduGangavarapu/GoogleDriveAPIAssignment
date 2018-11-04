<%@page import="org.json.JSONObject"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Home</title>
</head>
<body style="background-color: powderblue;">
	<div align="center">
		<h1>Google Drive API Assignment</h1>
	</div>

	<h2>View Files in Folders</h2>
	<form action="/GoogleDriveAPIAssesment/home" method="post">
		<b>Enter Folder Name :</b> <input type="text" name="folderName" />
		<button type="submit" name="button" value="button">Fetch</button>
	</form>

	<h3>Output :</h3>
	<br>
	<br>
	<%
		String fileList = (String) request.getAttribute("list");
		if (fileList != null) {
			out.print(fileList);
		}
	%>
</body>
</html>