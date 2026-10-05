package org.tiglor.system.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.tiglor.common.user.entity.Client;
import org.tiglor.common.user.mapper.ClientMapper;
import org.tiglor.system.service.ClientService;
import org.springframework.stereotype.Service;

@Service
public class ClientServiceImpl extends ServiceImpl<ClientMapper, Client> implements ClientService {
}
