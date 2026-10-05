package org.tiglor.common.log.aspect;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.common.core.support.TraceIds;
import org.tiglor.common.log.annotation.OperLog;
import org.tiglor.common.log.config.LogProperties;
import org.tiglor.common.log.entity.OperLogEntity;
import org.tiglor.common.log.sink.AuditJson;
import org.tiglor.common.log.sink.LogSink;
import org.tiglor.common.log.web.ClientAddress;

import java.util.Arrays;

/**
 * {@link OperLog} 的环绕通知：把一次管理动作的现场（谁、从哪个端、做了什么、成没成、多久）
 * 拼成一条审计记录交给 {@link LogSink}。
 * <p>
 * 两条硬约束：
 * <ul>
 *   <li>业务异常原样抛出 —— 切面只观察，不改变返回值和异常语义；</li>
 *   <li>取现场数据的全过程包在 try/catch 里 —— 审计自己出问题时只留一行 warn，
 *       不能把用户的删除请求变成 500。</li>
 * </ul>
 */
@Slf4j
@Aspect
public class OperLogAspect {

    private final LogSink sink;
    private final LogProperties props;

    public OperLogAspect(LogSink sink, LogProperties props) {
        this.sink = sink;
        this.props = props;
    }

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint point, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = point.proceed();
            return result;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            if (props.isOperEnabled()) {
                try {
                    sink.submit(build(point, operLog, result, error, System.currentTimeMillis() - start));
                } catch (Exception e) {
                    log.warn("操作审计记录失败 {}.{}：{}",
                            point.getTarget().getClass().getSimpleName(), point.getSignature().getName(), e.toString());
                }
            }
        }
    }

    private OperLogEntity build(ProceedingJoinPoint point, OperLog operLog,
                                Object result, Throwable error, long cost) {
        OperLogEntity record = new OperLogEntity();
        record.setTraceId(MDC.get(TraceIds.MDC_KEY));
        record.setTitle(operLog.title());
        record.setBusinessType(operLog.type().getCode());
        MethodSignature signature = (MethodSignature) point.getSignature();
        record.setMethod(signature.getDeclaringType().getSimpleName() + "." + signature.getName());
        record.setOperUserId(UserContext.getUserId());
        record.setOperClientKey(UserContext.getClientKey());
        record.setStatus(error == null ? 1 : 0);
        record.setCostTime(cost);
        if (error != null) {
            record.setErrorMsg(truncate(rootMessage(error), props.getMaxErrorLength()));
        }

        HttpServletRequest request = currentRequest();
        if (request != null) {
            record.setRequestMethod(request.getMethod());
            record.setOperUrl(request.getRequestURI());
            record.setOperIp(ClientAddress.ip(request));
        }
        if (operLog.saveParam()) {
            record.setOperParam(truncate(writeArgs(point.getArgs()), props.getMaxParamLength()));
        }
        if (operLog.saveResult() && result != null) {
            record.setJsonResult(truncate(AuditJson.write(result), props.getMaxResultLength()));
        }
        return record;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String writeArgs(Object[] args) {
        Object[] printable = Arrays.stream(args)
                // 这些类型要么序列化不出来（流、MultipartFile），要么是容器内部状态，没有审计价值
                .filter(a -> !(a instanceof ServletRequest || a instanceof ServletResponse
                        || a instanceof MultipartFile || a instanceof BindingResult
                        || a instanceof Resource))
                .toArray();
        if (printable.length == 0) {
            return null;
        }
        // 单个参数直接写对象，多个才包成数组，否则审计详情里全是 [{...}] 这种噪音
        return printable.length == 1 ? AuditJson.write(printable[0]) : AuditJson.write(printable);
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getClass().getSimpleName() + ": " + root.getMessage();
        return message;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...(共 " + value.length() + " 字符，已截断)";
    }
}
