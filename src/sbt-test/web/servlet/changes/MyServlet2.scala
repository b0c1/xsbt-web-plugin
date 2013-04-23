package test

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class MyServlet extends HttpServlet {

	val html = <HTML>
		<HEAD><TITLE>Hello, Scala 2!</TITLE></HEAD>
		<BODY>Hello, Scala 2! This is a servlet.</BODY>
	</HTML>.toString

	override def doGet(req:HttpServletRequest, resp:HttpServletResponse) {
		resp.setContentType("text/html")
    resp.getWriter().print(html)
	}
	def check28(f: Int = 3) = f
}

