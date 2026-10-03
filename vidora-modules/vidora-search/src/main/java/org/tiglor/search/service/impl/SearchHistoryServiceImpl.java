package org.tiglor.search.service.impl;

import org.tiglor.search.entity.SearchHistory;
import org.tiglor.search.mapper.SearchHistoryMapper;
import org.tiglor.search.service.SearchHistoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SearchHistoryServiceImpl extends ServiceImpl<SearchHistoryMapper, SearchHistory> implements SearchHistoryService {
}
