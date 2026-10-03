package org.tiglor.auth.service;

import org.tiglor.auth.dto.UserLoginDTO;
import org.tiglor.auth.dto.UserRegisterDTO;
import org.tiglor.auth.vo.LoginVO;

/** 统一身份认证用例。 */
public interface AuthService {

    LoginVO login(UserLoginDTO dto);

    Long register(UserRegisterDTO dto);
}
