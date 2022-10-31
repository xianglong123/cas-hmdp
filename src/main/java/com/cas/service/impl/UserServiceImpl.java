package com.cas.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cas.dto.LoginFormDTO;
import com.cas.dto.Result;
import com.cas.dto.UserDTO;
import com.cas.entity.User;
import com.cas.mapper.UserMapper;
import com.cas.service.IUserService;
import com.cas.utils.RegexUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.cas.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.cas.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.cas.utils.RedisConstants.LOGIN_USER_KEY;
import static com.cas.utils.RedisConstants.LOGIN_USER_TTL;
import static com.cas.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1、校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2、如果不是，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 3、符合，生成短信验证码
        String code = RandomUtil.randomNumbers(6);
        // 4、保存到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        session.setAttribute("code", code);
        // 5、发送验证码
        log.debug("发送短信验证码成功，验证码 {}", code );
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1、校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            return Result.fail("验证码不存在");
        }

        // 2、查询用户是否存在
        String phone = loginForm.getPhone();
        User user = query().eq("phone", phone).one();

        if (user == null) {
            // 不存在，添加用户
            user = createUserWithPhone(phone);
        }

        // 随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 将User对象转为HashMap储存
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(userDTO.getId()));
        userMap.put("icon", userDTO.getIcon());
        userMap.put("nickName", userDTO.getNickName());
        // 存储
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(key, userMap);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        // 新增用户
        save(user);
        return user;
    }


}
