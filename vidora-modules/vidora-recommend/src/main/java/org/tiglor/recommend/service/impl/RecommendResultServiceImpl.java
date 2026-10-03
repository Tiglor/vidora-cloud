package org.tiglor.recommend.service.impl;

import org.tiglor.recommend.entity.RecommendResult;
import org.tiglor.recommend.mapper.RecommendResultMapper;
import org.tiglor.recommend.service.RecommendResultService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RecommendResultServiceImpl extends ServiceImpl<RecommendResultMapper, RecommendResult> implements RecommendResultService {
}
