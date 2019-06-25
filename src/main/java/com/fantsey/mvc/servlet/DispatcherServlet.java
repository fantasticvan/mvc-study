package com.fantsey.mvc.servlet;

import com.fantsey.mvc.annotation.Autowired;
import com.fantsey.mvc.annotation.Controller;
import com.fantsey.mvc.annotation.RequestMapping;
import com.fantsey.mvc.annotation.Service;
import com.fantsey.mvc.controller.StudyController;
import com.fantsey.mvc.service.StudyService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @author fantsey
 * @date 2019/6/25
 */
public class DispatcherServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // 读取配置, 即mvc需要扫描的的包
    private Properties properties = new Properties();

    // 类的全路径名集合，将所有扫描到的类放到集合中
    private List<String> classNames = new ArrayList<>();

    // ioc， 所有的bean放到一个map中
    private Map<String, Object> ioc = new HashMap<>();

    // 保存uri和controller的方法实例关系
    private Map<String, Method> handlerMapping = new HashMap<>();

    // 保存uri和controller实例
    private Map<String, Object> controllerMap  = new HashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("************** init ***************");
        // 1.加载配置文件
        loadConfig(config.getInitParameter("contextConfigLocation"));

        // 2.初始化所有相关联的类,扫描用户配置的包下面所有的类
        String packageName = properties.getProperty("basePackage");
        System.out.println("packageName = " + packageName);
        doScanner(packageName);

        // 3.拿到扫描到的类,通过反射机制,实例化,并且放到ioc容器中(k-v beanName-bean) beanName默认是首字母小写
        doInstance();

        // 4.初始化HandlerMapping(将url和method对应上)
        initHandlerMapping();

        // 5.实现注入,主要针对service注入到controller
        doIoc();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            // 处理请求
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500!! Server Exception");
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        System.out.println("************** doDispatch ***************");
        if (handlerMapping.isEmpty())
            return;
        String uri = req.getRequestURI();
        String contextPath = req.getContextPath();
        // 拼接uri并把多个/替换成一个
        uri = uri.replace(contextPath, "").replaceAll("/+", "/");
        if (!handlerMapping.containsKey(uri)) {
            resp.getWriter().write("404 Not Found!!!");
        }
        Method method = this.handlerMapping.get(uri);

        // 获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 获取请求的参数
        Map<String, String[]> parameterMap = req.getParameterMap();
        // 保存参数值
        Object[] paramValues = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            String simpleName = parameterTypes[i].getSimpleName();
            if (simpleName.equals("HttpServletRequest")) {
                paramValues[i] = req;
                continue;
            }
            if (simpleName.equals("HttpServletResponse")) {
                paramValues[i] = resp;
                continue;
            }
            if (simpleName.equals("String")) {
                for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                    String value = Arrays.toString(entry.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
                    paramValues[i] = value;
                }
            }

            // 利用反射机制来调用方法
            method.invoke(this.controllerMap.get(uri), paramValues);
        }
    }


    private void doIoc() {
        System.out.println("************** doIoc ***************");
        if (ioc.isEmpty())
            return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] declaredFields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : declaredFields) {
                field.setAccessible(true); //可以访问私有属性
                if (field.isAnnotationPresent(Autowired.class)) {
                    Autowired annotation = field.getAnnotation(Autowired.class);
                    String key = annotation.value();
                    if ("".equals(key) || key == null) {
                        key = field.getName();
                    }
                    try {
                        field.set(entry.getValue(), ioc.get(key));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        StudyController testController = (StudyController) ioc.get("studyController");
        System.out.println(testController);

        StudyService testService = (StudyService) ioc.get("studyService");
        System.out.println(testService);
    }

    private void initHandlerMapping() {
        System.out.println("************** initHandlerMapping ***************");
        if (ioc.isEmpty())
            return;
        try {
            for (Map.Entry<String,Object> entry : ioc.entrySet()) {
                Class<?> clazz = entry.getValue().getClass();
                if (!clazz.isAnnotationPresent(Controller.class))
                    continue;
                // 拼url时,是controller类的uri拼上方法上的uri
                // 1、controller类上的url
                String baseUrl = "/";
                if (clazz.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping req = clazz.getAnnotation(RequestMapping.class);
                    baseUrl += req.value();
                }
                // 2、方法上的uri
                String url = "";
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    if (!method.isAnnotationPresent(RequestMapping.class))
                        continue;
                    RequestMapping req = method.getAnnotation(RequestMapping.class);
                    // 如果有重叠的/，如//，则替换为/
                    url = (baseUrl + "/" +  req.value()).replaceAll("/+", "/");
                    // 添加url和method的映射
                    handlerMapping.put(url, method);

                    // 添加url和controller对映射，可能存在重复构造Controller，因为ioc变量里可能已经存放了相应的实例
                    String key = toLowerFirstWord(clazz.getSimpleName());

                    // StudyController    map   =   "/study/test"  = new StudyController();
                    if (ioc.containsKey(key)){
                        controllerMap.put(url, ioc.get(key));
                    } else {
                        controllerMap.put(url, clazz.newInstance());
                    }
                    System.out.println(controllerMap.toString());
                    //ioc.put(url, clazz.newInstance()); // service和controller分开存放
                    System.out.println(url + "," + method);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doInstance() {
        System.out.println("************** doInstance ***************");
        if (classNames.isEmpty())
            return;

        for (String className : classNames){
            try {
                Class<?> clazz = Class.forName(className);
                // 通过类上添加的注解，来实例化对应的类, 默认的bean名是首字母小写
                // 1、@Controller注解的类
                if (clazz.isAnnotationPresent(Controller.class)) {
                    Controller controller = clazz.getAnnotation(Controller.class);
                    String key = controller.value();
                    if (!"".equals(key) && key != null){
                        ioc.put(key, clazz.newInstance());
                    } else {
                        // 只拿字节码上含有Controller.class 对象的信息
                        ioc.put(toLowerFirstWord(clazz.getSimpleName()), clazz.newInstance());
                    }
                // 2、@Service的类
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    Service service = clazz.getAnnotation(Service.class);
                    String key = service.value();
                    if (!"".equals(key) && key != null) {
                        ioc.put(key, clazz.newInstance());
                    } else {
                        // 只拿字节码上含有Controller.class 对象的信息
                        ioc.put(toLowerFirstWord(clazz.getSimpleName()), clazz.newInstance());
                    }
                } else {
                    continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private void doScanner(String basePackage) {
        if (!"".equals(basePackage) && basePackage != null){
            // 把所有的.替换成/     com.study.mvc   com/study/mvc
            URL url = this.getClass().getClassLoader().getResource("/"  + basePackage.replaceAll("\\.", "/"));
            File dir = new File(url.getFile());
            for (File file : dir.listFiles()){
                if (file.isDirectory()){
                    doScanner(basePackage + "." + file.getName());
                } else {
                    String className = basePackage + "." + file.getName().replace(".class", "");
                    classNames.add(className);
                    System.out.println("Spring容器中扫描到的类有： " + className);
                }
            }
        } else {
            System.out.println("扫描包未检测到！！！");
        }
    }



    private void loadConfig(String location) {
        System.out.println("************** loadConfig ***************");
        System.out.println("location = " + location);
        if (location.startsWith("classpath")) {
            location = location.replace("classpath:", "");
        } else if (location.contains("/")) {
            location = location.substring(location.indexOf("/") + 1, location.length());
        }

        // 把web.xml中的contextConfigLocation对应value值的文件加载到流里面
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (resourceAsStream != null) {
                try {
                    resourceAsStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 把字符串的首字母小写
     * @param name
     * @return
     */
    private String toLowerFirstWord(String name) {
        char[] charArray = name.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }
}
