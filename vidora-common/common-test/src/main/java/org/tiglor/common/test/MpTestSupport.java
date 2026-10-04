package org.tiglor.common.test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.baomidou.mybatisplus.spring.repository.CrudRepository;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.lang.reflect.Field;

/**
 * 脱离 Spring / MyBatis 容器直接 new 出 {@code ServiceImpl} 时需要的两步装配。
 * <p>
 * 抽出来是因为每个业务模块的 ServiceImpl 都要这么测，复制七八遍的话
 * 哪天 MyBatis-Plus 换了内部字段名就要改七八处。
 * </p>
 */
public final class MpTestSupport {

    private MpTestSupport() {
    }

    /**
     * 初始化实体的 lambda 列名缓存。
     * <p>
     * 没有 Spring 容器时 {@link TableInfoHelper} 里是空的，条件构造器第一次
     * {@code eq(Entity::getXxx, ...)} 就会因为解析不出列名而抛异常。
     * </p>
     */
    public static void initTableInfo(Class<?>... entities) {
        for (Class<?> entity : entities) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity);
        }
    }

    /**
     * 把 mock 的 Mapper 塞进 {@code ServiceImpl}。
     * <p>
     * MyBatis-Plus 3.5.14 的 {@code getEntityClass()} 是从 mapper 的 MapperProxy 上反推
     * mapper 接口的，mock 出来的 mapper 没有这层代理，所以除了 baseMapper
     * 还必须把已解析好的 mapperClass 直接写进去，否则 {@code lambdaQuery()} 一调就炸。
     * </p>
     *
     * @param service     被测的 ServiceImpl 实例
     * @param mapper      mock 出来的 Mapper
     * @param mapperClass 该 Mapper 的接口类型
     */
    public static void injectMapper(Object service, Object mapper, Class<?> mapperClass) {
        try {
            setField(service, CrudRepository.class, "baseMapper", mapper);
            setField(service, AbstractRepository.class, "mapperClass", mapperClass);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 mock Mapper 失败，MyBatis-Plus 的字段名可能变了", e);
        }
    }

    private static void setField(Object target, Class<?> owner, String name, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
