package org.tiglor.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tiglor.common.user.entity.User;
import com.baomidou.mybatisplus.spring.service.IService;

public interface UserService extends IService<User> {

    /**
     * 管理端分页。手机号/昵称模糊匹配，状态精确匹配，都不传就是按注册时间倒序翻全量。
     * <p>
     * 只有裸分页的话，几十万用户里找一个得点几十页——用户报障、客服工单都按手机号找人，
     * 所以这几个筛选条件是管理端的基本盘。
     * </p>
     */
    Page<User> pageUsers(long current, long size, String phone, String nickname, Integer status);
}
