package com.fantsey.mvc.service;

import com.fantsey.mvc.annotation.Service;

import java.util.Map;

/**
 * @author fantsey
 * @date 2019/6/25
 */
@Service
public class StudyService {

    public int insert(Map map) {
        System.out.println("调用了 StudyService 类中的方法:" + "insert");
        return 0;
    }

    public int delete(Map map) {
        System.out.println("调用了 StudyService 类中的方法:" + "delete");
        return 0;
    }

    public int update(Map map) {
        System.out.println("调用了 StudyService 类中的方法:" + "update");
        return 0;
    }

    public int select(Map map) {
        System.out.println("调用了 StudyService 类中的方法:" + "select");
        return 0;
    }
}
