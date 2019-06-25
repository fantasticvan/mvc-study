package com.fantsey.mvc.controller;

import com.fantsey.mvc.annotation.Autowired;
import com.fantsey.mvc.annotation.Controller;
import com.fantsey.mvc.annotation.RequestMapping;
import com.fantsey.mvc.annotation.RequestParam;
import com.fantsey.mvc.service.StudyService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author fantsey
 * @date 2019/6/25
 */
@Controller
@RequestMapping("study")
public class StudyController {

    @Autowired
    private StudyService studyService;

    @RequestMapping("/test")   //http://project:port/study/test
    public void test(HttpServletRequest request, HttpServletResponse response,@RequestParam("param") String param) {
        System.out.println(param);

        // 尝试调用service中方法
        studyService.insert(null);
        studyService.delete(null);
        studyService.update(null);
        studyService.select(null);

        try {
            //ModeAndView();
            response.getWriter().write("response.getWriter().write() \n");
            response.getWriter().write("doTest method success! param:" + param +"\n\n\n");

            response.getWriter().println("response.getWriter().println()");
            response.getWriter().println("doTest method success! param:" + param);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
