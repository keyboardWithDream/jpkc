package com.study.jpkc.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.study.jpkc.common.dto.RegisterMailDto;
import com.study.jpkc.entity.User;
import com.study.jpkc.mapper.UserMapper;
import com.study.jpkc.service.IUserService;
import com.study.jpkc.utils.RedisUtils;
import com.study.jpkc.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author isharlan.hu@gmail.com
 * @since 2020-12-18
 */
@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private RabbitMessagingTemplate messagingTemplate;

    private final int ACTIVATE_KEY_SAVE_TIME = 60 * 60 * 24 * 3;
    private final String DEFAULT_AVATAR = "https://study-1259382847.cos.ap-chongqing.myqcloud.com/jpck/user/avatar/default/default_avatar.jpg";

    @Override
    public boolean save(User user) {
        if (StringUtils.isBlank(user.getUserAvatar())) {
            user.setUserAvatar(DEFAULT_AVATAR);
        }
        String password = user.getPassword();
        user.setPassword(new SimpleHash("MD5", password).toHex());
        user.setUserId(UUID.randomUUID().toString().replace("-", ""));
        if (userMapper.insert(user) == 0) {
            return false;
        }
        sendRegisterMail(user);
        return true;
    }

    @Override
    public User getUserByEmail(String email) {
        return userMapper.selectOne(new QueryWrapper<User>().eq("user_email", email));
    }

    @Override
    public User getUserByUsername(String username) {
        return userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
    }

    @Override
    public User getUserByPhone(String phone) {
        return userMapper.selectOne(new QueryWrapper<User>().eq("user_phone", phone));
    }

    @Override
    public boolean saveUserByEmail(String email, String password) {
        User user = getDefaultUserInfo(email, password);
        if (userMapper.insert(user) == 1) {
            sendRegisterMail(user);
            return true;
        }
        return false;
    }

    @Override
    public boolean saveUserByPhone(String phone, String password) {
        User user = getDefaultUserInfo(phone, password);
        return userMapper.insert(user) == 1;
    }

    @Override
    public boolean updateUserStatusByUsername(String username, Integer status) {
        return userMapper.updateUserStatusByUsername(username, status) == 1;
    }

    /**
     * 传入用户信息返回一个初始化用户
     * @param info 用户信息
     * @return 初始化用户
     */
    private User getDefaultUserInfo(String info, String password) {
        User user = new User();
        user.setUserId(UUID.randomUUID().toString().replace("-", ""));
        user.setPassword(new SimpleHash("MD5", password).toHex());
        user.setUserSex(0);
        user.setUserAvatar(DEFAULT_AVATAR);
        user.setUserStatus(1);
        if (RegexUtils.emailMatches(info)) {
            user.setUsername(info);
            user.setUserEmail(info);
        }
        if (RegexUtils.phoneMatches(info)) {
            user.setUsername(info);
            user.setUserPhone(info);
        }
        return user;
    }

    /**
     * 发送注册邮件
     * @param user 用户信息
     */
    private void sendRegisterMail(User user) {
        //加密邮件激活密钥地址 MD5 密钥=用户名+盐+userId 的前16位
        String salt = "lb82ndLF";
        String activateKey = user.getUsername() + salt + user.getUserId();
        String encryptionKey = DigestUtil.md5Hex(activateKey);
        //信息存入redis key=activateKey[0,16]:value={key: encryptionKey[16,30], username:username, userId:userId, salt:salt}
        Map<String, Object> keyMap = new HashMap<>(4);
        keyMap.put("key", encryptionKey.substring(16));
        keyMap.put("username", user.getUsername());
        keyMap.put("userId", user.getUserId());
        keyMap.put("salt", salt);
        redisUtils.setHash(encryptionKey.substring(0, 16), keyMap, ACTIVATE_KEY_SAVE_TIME);
        messagingTemplate.convertAndSend("amq.direct", "user.register.mail", new RegisterMailDto(encryptionKey.substring(0, 16), user));
    }
}
