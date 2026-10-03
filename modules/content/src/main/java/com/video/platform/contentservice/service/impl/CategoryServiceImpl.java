package com.video.platform.contentservice.service.impl;

import com.video.platform.contentservice.entity.Category;
import com.video.platform.contentservice.mapper.CategoryMapper;
import com.video.platform.contentservice.service.CategoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {
}
