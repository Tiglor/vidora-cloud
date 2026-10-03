package org.tiglor.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.tiglor.system.entity.User;
import org.tiglor.system.mapper.UserMapper;
import org.tiglor.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
