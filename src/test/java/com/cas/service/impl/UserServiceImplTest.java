package com.cas.service.impl;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cas.BaseApplication;
import com.cas.dto.LoginFormDTO;
import com.cas.dto.Result;
import com.cas.entity.User;
import com.cas.service.IUserService;
import com.cas.utils.RandomPhoneNumber;
import org.junit.Test;

import javax.annotation.Resource;
import javax.management.Query;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author xiang_long
 * @version 1.0
 * @date 2022/11/1 5:34 下午
 * @desc
 */
public class UserServiceImplTest extends BaseApplication {

    @Resource
    private IUserService userService;

    @Test
    public void add() {
        LoginFormDTO dto = new LoginFormDTO();
        for (int i = 0; i < 100; i ++) {
            String mobile = RandomPhoneNumber.createMobile(1);
            // 注册10000用户
            dto.setPhone(mobile);
            userService.login(dto,null);
        }
    }

    /**
     * 登录1000个用户，并将token写入文件
     * @throws IOException
     */
    @Test
    public void login() throws IOException {
        File file = new File("/Users/xianglong/IdeaProjects/cas-hmdp/src/main/resources/token.txt");
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileOutputStream fos = new FileOutputStream(file);


        // 登录
        Page<User> page = userService.page(new Page<>(1, 1000));
        List<User> users = page.getRecords();
        LoginFormDTO dto = new LoginFormDTO();
        users.forEach(user -> {
            dto.setPhone(user.getPhone());
            Result result = userService.login(dto, null);
            try {
                fos.write(result.getData().toString().getBytes());
                fos.write("\n".getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        fos.close();
    }



}