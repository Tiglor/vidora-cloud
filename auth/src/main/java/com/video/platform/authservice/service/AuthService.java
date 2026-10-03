package com.video.platform.authservice.service;

import com.video.platform.authservice.dto.UserLoginDTO;
import com.video.platform.authservice.dto.UserRegisterDTO;
import com.video.platform.authservice.vo.LoginVO;

/** 统一身份认证用例。 */
public interface AuthService {

    LoginVO login(UserLoginDTO dto);

    Long register(UserRegisterDTO dto);
}
