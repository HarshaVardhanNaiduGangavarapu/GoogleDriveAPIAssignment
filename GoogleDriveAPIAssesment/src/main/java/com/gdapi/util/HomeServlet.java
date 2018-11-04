package com.gdapi.util;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

import org.json.JSONObject;

/***
 * 
 * @author Harsha Vardhan Naidu Gangavarapu
 *
 */
public class HomeServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			GoogleDriveUtil driveUtil = new GoogleDriveUtil();
			String folderName = req.getParameter("folderName");
			JSONObject json = new JSONObject();
			json.put("folderName", folderName);
			Response response = driveUtil.getFilesListInFolder(json.toString());
			req.setAttribute("list", response.getEntity().toString());
			req.getRequestDispatcher("index.jsp").forward(req, resp);
		} catch (Exception e) {
			req.setAttribute("list", e.getMessage());
			req.getRequestDispatcher("index.jsp").forward(req, resp);
		}
	}
}
