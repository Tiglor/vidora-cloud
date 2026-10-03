package com.video.platform.searchservice.service.impl;

import com.video.platform.searchservice.entity.SearchHistory;
import com.video.platform.searchservice.mapper.SearchHistoryMapper;
import com.video.platform.searchservice.service.SearchHistoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SearchHistoryServiceImpl extends ServiceImpl<SearchHistoryMapper, SearchHistory> implements SearchHistoryService {
}
