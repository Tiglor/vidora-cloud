package org.tiglor.system.dubbo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.tiglor.api.system.RemoteClientApi;
import org.tiglor.api.system.dto.RemoteClientDTO;
import org.tiglor.system.entity.Client;
import org.tiglor.system.mapper.ClientMapper;

/**
 * {@link RemoteClientApi} 的 Dubbo provider。
 * <p>
 * sys_client 是「哪个端在调」的注册表，本来就只有 system-service 在管（管理端的客户端 CRUD 也在这里），
 * 登录时的校验读的是同一张表，所以走 RPC 而不是让 auth-service 再连一次库。
 */
@DubboService
@RequiredArgsConstructor
public class RemoteClientApiImpl implements RemoteClientApi {

    private final ClientMapper clientMapper;

    @Override
    public RemoteClientDTO getByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        Client client = clientMapper.selectOne(
                new LambdaQueryWrapper<Client>().eq(Client::getClientId, clientId));
        if (client == null) {
            return null;
        }
        RemoteClientDTO dto = new RemoteClientDTO();
        dto.setClientId(client.getClientId());
        dto.setClientKey(client.getClientKey());
        dto.setDeviceType(client.getDeviceType());
        dto.setGrantType(client.getGrantType());
        dto.setTimeout(client.getTimeout());
        dto.setStatus(client.getStatus());
        return dto;
    }
}
